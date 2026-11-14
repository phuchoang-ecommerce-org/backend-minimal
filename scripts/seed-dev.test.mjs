import assert from 'node:assert/strict';
import test from 'node:test';

test('dev seed gives every product a category image with CC0 provenance', async () => {
  let payload;
  globalThis.fetch = async (_url, options) => {
    payload = JSON.parse(options.body);
    return { status: 204 };
  };

  await import('./seed-dev.mjs');

  assert.equal(payload.data.products.length, 50);
  const imageUrlsByCategory = new Map();
  for (const product of payload.data.products) {
    assert.ok(product.image, `Missing image for ${product.title}`);
    assert.match(product.image.id, /^[0-9a-f]{8}-[0-9a-f-]{27}$/);
    assert.match(product.image.url, /^https:\/\/upload\.wikimedia\.org\/wikipedia\/commons\//);
    assert.match(product.image.sourceName, /^Wikimedia Commons: https:\/\/commons\.wikimedia\.org\/wiki\/File:/);
    assert.equal(product.image.license, 'https://creativecommons.org/publicdomain/zero/1.0/');
    assert.ok(product.image.altText.length > 0);
    const previousUrl = imageUrlsByCategory.get(product.categoryId);
    if (previousUrl) assert.equal(product.image.url, previousUrl);
    imageUrlsByCategory.set(product.categoryId, product.image.url);
  }
  assert.equal(imageUrlsByCategory.size, 5);
  assert.equal(new Set(imageUrlsByCategory.values()).size, 5);
});
