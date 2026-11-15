#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const DEFAULT_OPTIONS = {
  database: 'marketplace_benchmark_b0', seed: 20261123, members: 1_000, sellers: 100,
  categories: 20, products: 10_000, variantsPerProduct: 2, historicalOrders: 50_000,
  stockPerVariant: 10_000, password: 'B0SyntheticOnly2026!',
  passwordHash: '$2y$10$TS77NkfACEQMOjq.Sd/rMer5oLKYa4uxcfkt13onN0wQ0pdij1XqS',
};

export function generateB0Fixture(input = {}) {
  const options = { ...DEFAULT_OPTIONS, ...input };
  validate(options);
  const counts = {
    members: options.members, sellers: options.sellers, categories: options.categories,
    products: options.products, variants: options.products * options.variantsPerProduct,
    historicalOrders: options.historicalOrders, historicalSellerOrders: options.historicalOrders * 2,
    historicalOrderLines: options.historicalOrders * 2, addresses: options.members, carts: options.members,
  };
  const fixture = {
    metadata: {
      phase: 'B0', seed: options.seed, database: options.database,
      generatedAt: 'deterministic-from-script', counts, credentialClass: 'synthetic-disposable-only',
    },
    public: {
      categoryId: uuid(options.seed, 'category', 0), productId: uuid(options.seed, 'product', 0),
      commonSearch: 'Synthetic Common', rareSearch: `RareToken${options.products - 1}`,
      noResultSearch: `B0NoResult${options.seed}`,
    },
    workers: Array.from({ length: options.members }, (_, index) => ({
      login: memberLogin(index), password: options.password,
      addressId: uuid(options.seed, 'address', index),
      variantIds: [
        uuid(options.seed, 'variant', (index * 2) * options.variantsPerProduct),
        uuid(options.seed, 'variant', (index * 2 + 1) * options.variantsPerProduct),
      ],
    })),
  };
  const sql = seedSql(options, counts);
  fixture.metadata.seedSqlSha256 = createHash('sha256').update(sql).digest('hex');
  return { fixture, sql };
}

function seedSql(o, counts) {
  return `\\set ON_ERROR_STOP on
BEGIN;

DO $guard$
BEGIN
  IF current_database() <> ${sqlLiteral(o.database)} THEN
    RAISE EXCEPTION 'B0 seed target mismatch: expected %, got %', ${sqlLiteral(o.database)}, current_database();
  END IF;
  IF to_regclass('marketplace.account') IS NULL OR to_regclass('marketplace.product') IS NULL THEN
    RAISE EXCEPTION 'Apply Flyway migrations before loading the B0 seed';
  END IF;
  IF EXISTS (SELECT 1 FROM marketplace.account LIMIT 1)
     OR EXISTS (SELECT 1 FROM marketplace.product LIMIT 1)
     OR EXISTS (SELECT 1 FROM marketplace.customer_order LIMIT 1) THEN
    RAISE EXCEPTION 'Disposable benchmark database is not empty; recreate it instead of truncating data';
  END IF;
END
$guard$;

CREATE FUNCTION pg_temp.b0_uuid(kind text, item_index bigint) RETURNS uuid
LANGUAGE sql IMMUTABLE STRICT AS $uuid$
  WITH digest AS (SELECT md5('${o.seed}:' || kind || ':' || item_index::text) AS value)
  SELECT (substr(value,1,8) || '-' || substr(value,9,4) || '-4' || substr(value,14,3)
    || '-a' || substr(value,18,3) || '-' || substr(value,21,12))::uuid FROM digest
$uuid$;

CREATE FUNCTION pg_temp.b0_price(product_index bigint, variant_offset bigint) RETURNS numeric
LANGUAGE sql IMMUTABLE STRICT AS $price$
  SELECT (9.50 + mod(product_index * ${o.variantsPerProduct} + variant_offset, 200) * 0.25)::numeric(12,2)
$price$;

INSERT INTO marketplace.account (id,normalized_login,password_hash,role,status,display_name)
SELECT pg_temp.b0_uuid('member-account',i),
  'b0.member.'||lpad((i+1)::text,6,'0')||'@example.test', ${sqlLiteral(o.passwordHash)},
  'MEMBER','ACTIVE','B0 Synthetic Member '||lpad((i+1)::text,6,'0')
FROM generate_series(0, ${o.members - 1}) AS source(i);

INSERT INTO marketplace.account (id,normalized_login,password_hash,role,status,display_name)
SELECT pg_temp.b0_uuid('seller-account',i),
  'b0.seller.'||lpad((i+1)::text,4,'0')||'@example.test', ${sqlLiteral(o.passwordHash)},
  'SELLER','ACTIVE','B0 Synthetic Seller '||lpad((i+1)::text,4,'0')
FROM generate_series(0, ${o.sellers - 1}) AS source(i);

INSERT INTO marketplace.seller_shop (id,account_id,name,status)
SELECT pg_temp.b0_uuid('seller-shop',i),pg_temp.b0_uuid('seller-account',i),
  'B0 Synthetic Shop '||lpad((i+1)::text,4,'0'),'ACTIVE'
FROM generate_series(0, ${o.sellers - 1}) AS source(i);

INSERT INTO marketplace.address (id,member_id,recipient,address_line,locality,postal_code,country)
SELECT pg_temp.b0_uuid('address',i),pg_temp.b0_uuid('member-account',i),
  'B0 Member '||lpad((i+1)::text,6,'0'),(i+1)::text||' Benchmark Street',
  'Fixture City',lpad(mod(i,100000)::text,5,'0'),'US'
FROM generate_series(0, ${o.members - 1}) AS source(i);

INSERT INTO marketplace.cart (id,member_id)
SELECT pg_temp.b0_uuid('cart',i),pg_temp.b0_uuid('member-account',i)
FROM generate_series(0, ${o.members - 1}) AS source(i);

INSERT INTO marketplace.category (id,name)
SELECT pg_temp.b0_uuid('category',i),'B0 Synthetic Category '||lpad((i+1)::text,3,'0')
FROM generate_series(0, ${o.categories - 1}) AS source(i);

INSERT INTO marketplace.product (id,seller_id,category_id,title,description,published)
SELECT pg_temp.b0_uuid('product',i),pg_temp.b0_uuid('seller-shop',mod(i,${o.sellers})),
  pg_temp.b0_uuid('category',mod(i,${o.categories})),
  'Synthetic Common Product '||lpad((i+1)::text,6,'0')
    ||CASE WHEN i=${o.products - 1} THEN ' RareToken${o.products - 1}' ELSE '' END,
  'Synthetic Common large-scale B0 catalog product',
  (i < ${o.members * 2} OR mod(i,20)<>0)
FROM generate_series(0, ${o.products - 1}) AS source(i);

INSERT INTO marketplace.variant (id,product_id,sku,label,price_usd)
SELECT pg_temp.b0_uuid('variant',p.i*${o.variantsPerProduct}+v.variant_offset),
  pg_temp.b0_uuid('product',p.i),
  'B0-SKU-'||lpad((p.i*${o.variantsPerProduct}+v.variant_offset)::text,12,'0'),
  'Option '||(v.variant_offset+1),pg_temp.b0_price(p.i,v.variant_offset)
FROM generate_series(0, ${o.products - 1}) AS p(i)
CROSS JOIN generate_series(0, ${o.variantsPerProduct - 1}) AS v(variant_offset);

INSERT INTO marketplace.stock (variant_id,on_hand)
SELECT pg_temp.b0_uuid('variant',i),${o.stockPerVariant}
FROM generate_series(0, ${counts.variants - 1}) AS source(i);

INSERT INTO marketplace.customer_order
  (id,member_id,idempotency_key,request_fingerprint,status,total_usd,currency,created_at)
SELECT pg_temp.b0_uuid('order',i),pg_temp.b0_uuid('member-account',mod(i,${o.members})),
  'b0-history-'||i,md5('${o.seed}:history:'||i::text),'PLACED',
  (pg_temp.b0_price(mod(i*2,${o.products}),0)
   +pg_temp.b0_price(mod(i*2+1,${o.products}),0))::numeric(12,2),'USD',
  timestamptz '2025-01-01 00:00:00+00'+i*interval '1 second'
FROM generate_series(0, ${o.historicalOrders - 1}) AS source(i);

INSERT INTO marketplace.order_address_snapshot
  (customer_order_id,recipient,address_line,locality,postal_code,country)
SELECT pg_temp.b0_uuid('order',i),'B0 Member '||lpad((mod(i,${o.members})+1)::text,6,'0'),
  (mod(i,${o.members})+1)::text||' Benchmark Street','Fixture City',
  lpad(mod(i,100000)::text,5,'0'),'US'
FROM generate_series(0, ${o.historicalOrders - 1}) AS source(i);

INSERT INTO marketplace.seller_order
  (id,customer_order_id,seller_id,payment_status,total_usd,paid_at)
SELECT pg_temp.b0_uuid('seller-order',orders.i*2+line.line_index),
  pg_temp.b0_uuid('order',orders.i),
  pg_temp.b0_uuid('seller-shop',mod(mod(orders.i*2+line.line_index,${o.products}),${o.sellers})),
  CASE WHEN mod(orders.i*2+line.line_index,4)=0 THEN 'PAID' ELSE 'PENDING_PAYMENT' END,
  pg_temp.b0_price(mod(orders.i*2+line.line_index,${o.products}),0),
  CASE WHEN mod(orders.i*2+line.line_index,4)=0
    THEN timestamptz '2025-01-01 00:05:00+00'+orders.i*interval '1 second' ELSE NULL END
FROM generate_series(0, ${o.historicalOrders - 1}) AS orders(i)
CROSS JOIN generate_series(0,1) AS line(line_index);

INSERT INTO marketplace.order_line
  (id,seller_order_id,variant_id,product_id,product_title_snapshot,sku_snapshot,
   quantity,unit_price_usd,discount_usd,line_total_usd)
SELECT pg_temp.b0_uuid('order-line',orders.i*2+line.line_index),
  pg_temp.b0_uuid('seller-order',orders.i*2+line.line_index),
  pg_temp.b0_uuid('variant',mod(orders.i*2+line.line_index,${o.products})*${o.variantsPerProduct}),
  pg_temp.b0_uuid('product',mod(orders.i*2+line.line_index,${o.products})),
  'Synthetic Common Product '||lpad((mod(orders.i*2+line.line_index,${o.products})+1)::text,6,'0'),
  'B0-SKU-'||lpad((mod(orders.i*2+line.line_index,${o.products})*${o.variantsPerProduct})::text,12,'0'),
  1,pg_temp.b0_price(mod(orders.i*2+line.line_index,${o.products}),0),0,
  pg_temp.b0_price(mod(orders.i*2+line.line_index,${o.products}),0)
FROM generate_series(0, ${o.historicalOrders - 1}) AS orders(i)
CROSS JOIN generate_series(0,1) AS line(line_index);

INSERT INTO marketplace.audit_entry
  (id,actor_id,action,resource_type,resource_id,correlation_id,created_at)
SELECT pg_temp.b0_uuid('audit',i),pg_temp.b0_uuid('member-account',mod(i,${o.members})),
  'OrderPlaced','CustomerOrder',pg_temp.b0_uuid('order',i),
  pg_temp.b0_uuid('audit-correlation',i),
  timestamptz '2025-01-01 00:00:00+00'+i*interval '1 second'
FROM generate_series(0, ${o.historicalOrders - 1}) AS source(i);

DO $verify$
DECLARE actual bigint;
BEGIN
  SELECT count(*) INTO actual FROM marketplace.account WHERE normalized_login LIKE 'b0.member.%@example.test';
  IF actual<>${counts.members} THEN RAISE EXCEPTION 'B0 member count %, expected ${counts.members}',actual; END IF;
  SELECT count(*) INTO actual FROM marketplace.seller_shop;
  IF actual<>${counts.sellers} THEN RAISE EXCEPTION 'B0 seller count %, expected ${counts.sellers}',actual; END IF;
  SELECT count(*) INTO actual FROM marketplace.product;
  IF actual<>${counts.products} THEN RAISE EXCEPTION 'B0 product count %, expected ${counts.products}',actual; END IF;
  SELECT count(*) INTO actual FROM marketplace.variant;
  IF actual<>${counts.variants} THEN RAISE EXCEPTION 'B0 variant count %, expected ${counts.variants}',actual; END IF;
  SELECT count(*) INTO actual FROM marketplace.customer_order;
  IF actual<>${counts.historicalOrders} THEN RAISE EXCEPTION 'B0 order count %, expected ${counts.historicalOrders}',actual; END IF;
  SELECT count(*) INTO actual FROM marketplace.order_line;
  IF actual<>${counts.historicalOrderLines} THEN RAISE EXCEPTION 'B0 order-line count %, expected ${counts.historicalOrderLines}',actual; END IF;
END
$verify$;

ANALYZE marketplace.account;
ANALYZE marketplace.address;
ANALYZE marketplace.category;
ANALYZE marketplace.product;
ANALYZE marketplace.variant;
ANALYZE marketplace.stock;
ANALYZE marketplace.cart;
ANALYZE marketplace.customer_order;
ANALYZE marketplace.seller_order;
ANALYZE marketplace.order_line;
ANALYZE marketplace.order_address_snapshot;
ANALYZE marketplace.audit_entry;
COMMIT;
`;
}

function validate(o) {
  if (!/^[a-z][a-z0-9_]{2,62}$/.test(o.database)) throw new Error('database must be a safe PostgreSQL identifier');
  for (const field of ['seed','members','sellers','categories','products','variantsPerProduct','historicalOrders','stockPerVariant']) {
    if (!Number.isSafeInteger(o[field]) || o[field] < 1) throw new Error(`${field} must be a positive safe integer`);
  }
  if (o.sellers < 2) throw new Error('B0 requires at least two Sellers');
  if (o.products < o.members * 2) throw new Error('B0 requires at least two products per Member');
  if (o.products <= 100) throw new Error('B0 cursor coverage requires more than 100 products');
  if (!o.password || o.password.length < 12) throw new Error('password must contain at least 12 characters');
  if (!/^\$2[aby]\$\d{2}\$/.test(o.passwordHash)) throw new Error('passwordHash must be BCrypt');
}

function uuid(seed, kind, index) {
  const value = createHash('md5').update(`${seed}:${kind}:${index}`).digest('hex');
  return `${value.slice(0,8)}-${value.slice(8,12)}-4${value.slice(13,16)}-a${value.slice(17,20)}-${value.slice(20,32)}`;
}

function memberLogin(index) {
  return `b0.member.${String(index + 1).padStart(6,'0')}@example.test`;
}

function sqlLiteral(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function parseArguments(argv) {
  const result = {};
  const names = new Map([
    ['--database','database'],['--seed','seed'],['--members','members'],['--sellers','sellers'],
    ['--categories','categories'],['--products','products'],['--variants-per-product','variantsPerProduct'],
    ['--historical-orders','historicalOrders'],['--stock-per-variant','stockPerVariant'],['--output-dir','outputDir'],
  ]);
  for (let index = 0; index < argv.length; index += 2) {
    const field = names.get(argv[index]);
    if (!field || argv[index + 1] === undefined) throw new Error(`Unknown or incomplete argument: ${argv[index]}`);
    result[field] = field === 'database' || field === 'outputDir' ? argv[index + 1] : Number(argv[index + 1]);
  }
  return result;
}

function main() {
  const args = parseArguments(process.argv.slice(2));
  const password = process.env.B0_FIXTURE_PASSWORD;
  const passwordHash = process.env.B0_FIXTURE_PASSWORD_HASH;
  if (Boolean(password) !== Boolean(passwordHash)) {
    throw new Error('Set B0_FIXTURE_PASSWORD and B0_FIXTURE_PASSWORD_HASH together');
  }
  const generated = generateB0Fixture({
    ...args, ...(password ? { password, passwordHash } : {}),
  });
  const outputDir = resolve(args.outputDir || 'docs/benchmark/scripts/generated');
  mkdirSync(outputDir, { recursive: true });
  const fixturePath = resolve(outputDir, 'b0-fixture.json');
  const sqlPath = resolve(outputDir, 'b0-seed.sql');
  writeFileSync(fixturePath, `${JSON.stringify(generated.fixture, null, 2)}\n`, { mode: 0o600 });
  writeFileSync(sqlPath, generated.sql, { mode: 0o600 });
  process.stdout.write(`Generated ${fixturePath}\nGenerated ${sqlPath}\n`);
  process.stdout.write(`${JSON.stringify(generated.fixture.metadata.counts)}\n`);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) main();
