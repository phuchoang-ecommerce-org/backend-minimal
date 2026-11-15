import assert from 'node:assert/strict';
import test from 'node:test';
import { generateB0Fixture } from './generate-b0-fixture.mjs';

const options = {
  database: 'marketplace_benchmark_b0',
  seed: 20261123,
  members: 1_000,
  sellers: 100,
  categories: 20,
  products: 10_000,
  variantsPerProduct: 2,
  historicalOrders: 50_000,
  stockPerVariant: 10_000,
  password: 'B0SyntheticOnly2026!',
  passwordHash: '$2y$10$TS77NkfACEQMOjq.Sd/rMer5oLKYa4uxcfkt13onN0wQ0pdij1XqS',
};

test('generates the approved large-scale B0 fixture and guarded SQL', () => {
  const generated = generateB0Fixture(options);

  assert.equal(generated.fixture.metadata.counts.members, 1_000);
  assert.equal(generated.fixture.metadata.counts.sellers, 100);
  assert.equal(generated.fixture.metadata.counts.products, 10_000);
  assert.equal(generated.fixture.metadata.counts.variants, 20_000);
  assert.equal(generated.fixture.metadata.counts.historicalOrders, 50_000);
  assert.equal(generated.fixture.metadata.counts.historicalOrderLines, 100_000);
  assert.equal(generated.fixture.workers.length, 1_000);
  assert.equal(new Set(generated.fixture.workers.map(worker => worker.login)).size, 1_000);
  assert.equal(new Set(generated.fixture.workers.map(worker => worker.addressId)).size, 1_000);
  assert.ok(generated.fixture.workers.every(worker => worker.variantIds.length === 2));
  assert.ok(generated.fixture.workers.every(worker => worker.variantIds[0] !== worker.variantIds[1]));
  assert.ok(generated.fixture.workers.every((_, index) =>
    (index * 2) % options.sellers !== (index * 2 + 1) % options.sellers));
  assert.equal(generated.fixture.workers[0].password, options.password);

  assert.match(generated.sql, /current_database\(\).*marketplace_benchmark_b0/s);
  assert.match(generated.sql, /Disposable benchmark database is not empty/);
  assert.match(generated.sql, /generate_series\(0, 9999\)/);
  assert.match(generated.sql, /generate_series\(0, 49999\)/);
  assert.match(generated.sql, /INSERT INTO marketplace\.order_line/);
  assert.match(generated.sql, /ANALYZE marketplace\.product/);
  assert.doesNotMatch(generated.sql, /\bTRUNCATE\b/i);
  assert.doesNotMatch(generated.sql, new RegExp(options.password.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.doesNotMatch(JSON.stringify(generated.fixture), /\$2[aby]\$/);
});

test('rejects a fixture that cannot assign two products per worker', () => {
  assert.throws(
    () => generateB0Fixture({ ...options, members: 60, products: 100 }),
    /at least two products per Member/,
  );
});

test('rejects an unsafe database identifier', () => {
  assert.throws(
    () => generateB0Fixture({ ...options, database: 'postgres; drop database postgres' }),
    /safe PostgreSQL identifier/,
  );
});
