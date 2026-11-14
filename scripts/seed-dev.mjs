#!/usr/bin/env node
import { createHash } from 'node:crypto';

const base = process.env.DEV_SEED_BASE_URL ?? 'http://127.0.0.1:8080';
const url = new URL('/dev/seed', base);
if (!['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) {
  throw new Error('The dev seed script only connects to a loopback server.');
}

const uuid = (kind, index) => {
  const hex = createHash('sha256').update(`marketplace-dev-v1:${kind}:${index}`).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
};
const categories = ['Books', 'Electronics', 'Home', 'Sports', 'Clothing'].map((name, i) => ({
  id: uuid('category', i), name: `Synthetic ${name}`,
}));
// Each file page confirms the image is CC0. Keep its source page with the seeded URL.
const cc0License = 'https://creativecommons.org/publicdomain/zero/1.0/';
const categoryImages = [
  {
    url: 'https://upload.wikimedia.org/wikipedia/commons/1/14/Book_still_photography.jpg',
    sourcePage: 'https://commons.wikimedia.org/wiki/File:Book_still_photography.jpg',
    altText: 'Books arranged on a table',
  },
  {
    url: 'https://upload.wikimedia.org/wikipedia/commons/a/a6/Headphones_on_wood_in_black_and_white_%28Unsplash%29.jpg',
    sourcePage: 'https://commons.wikimedia.org/wiki/File:Headphones_on_wood_in_black_and_white_(Unsplash).jpg',
    altText: 'Headphones resting on a wooden surface',
  },
  {
    url: 'https://upload.wikimedia.org/wikipedia/commons/b/bc/Room_Lamp.jpg',
    sourcePage: 'https://commons.wikimedia.org/wiki/File:Room_Lamp.jpg',
    altText: 'Lamp in a room',
  },
  {
    url: 'https://upload.wikimedia.org/wikipedia/commons/d/d0/Running_shoes_display_2.JPG',
    sourcePage: 'https://commons.wikimedia.org/wiki/File:Running_shoes_display_2.JPG',
    altText: 'Running shoes on display',
  },
  {
    url: 'https://upload.wikimedia.org/wikipedia/commons/5/55/T-shirt.png',
    sourcePage: 'https://commons.wikimedia.org/wiki/File:T-shirt.png',
    altText: 'Drawing of a plain T-shirt',
  },
];
const products = Array.from({ length: 50 }, (_, i) => ({
  id: uuid('product', i),
  categoryId: categories[i % categories.length].id,
  variantId: uuid('variant', i),
  title: `Synthetic ${categories[i % categories.length].name.slice(10)} Item ${String(i + 1).padStart(2, '0')}`,
  sku: `SYNTHETIC-${String(i + 1).padStart(4, '0')}`,
  priceUsd: Number((9.5 + (i % 17) * 3.25).toFixed(2)),
  stock: 10 + (i % 15),
  image: {
    id: uuid('image', i),
    url: categoryImages[i % categoryImages.length].url,
    sourceName: `Wikimedia Commons: ${categoryImages[i % categoryImages.length].sourcePage}`,
    license: cc0License,
    altText: categoryImages[i % categoryImages.length].altText,
  },
}));
const addresses = [0, 1].map(i => ({
  id: uuid('address', i), recipient: 'Synthetic Member',
  addressLine: `${i + 10} Example Street`, locality: 'Test City',
  postalCode: '10000', country: 'US',
}));
const orders = Array.from({ length: 20 }, (_, i) => ({
  id: uuid('order', i), sellerOrderId: uuid('seller-order', i), lineId: uuid('order-line', i),
  variantId: products[i].variantId, quantity: 1 + (i % 3),
  paymentStatus: i % 3 === 0 ? 'PAID' : 'PENDING_PAYMENT',
}));
const reviews = Array.from({ length: 20 }, (_, i) => ({
  id: uuid('review', i), productId: products[i].id, rating: 1 + (i % 5),
  body: `Synthetic review for item ${i + 1}`,
}));

const response = await fetch(url, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({
    memberPassword: 'DevMember2026!OnlyLocal',
    sellerPassword: 'DevSeller2026!OnlyLocal',
    adminPassword: 'DevAdmin2026!OnlyLocal',
    data: { categories, products, addresses, orders, reviews },
  }),
});
if (response.status !== 204) {
  throw new Error(`Dev seed failed: HTTP ${response.status}`);
}
console.log(`Seeded 3 accounts, 1 shop, ${categories.length} categories, ${products.length} products, ` +
  `${products.length} linked images, ${addresses.length} addresses, ${orders.length} orders, and ${reviews.length} reviews.`);
