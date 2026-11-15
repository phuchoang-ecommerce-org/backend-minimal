import assert from 'node:assert/strict';
import test from 'node:test';
import { jsonSemanticallyEqual } from './canonical-json.mjs';

test('treats objects with different key insertion order as equal', () => {
  const created = {
    id: 'order-1',
    sellerOrders: [{ id: 'portion-1', totalUsd: '12.50' }],
    address: { recipient: 'Member', country: 'US' },
  };
  const replay = {
    address: { country: 'US', recipient: 'Member' },
    sellerOrders: [{ totalUsd: '12.50', id: 'portion-1' }],
    id: 'order-1',
  };

  assert.equal(jsonSemanticallyEqual(created, replay), true);
});

test('retains array order and detects changed values', () => {
  assert.equal(jsonSemanticallyEqual({ lines: ['a', 'b'] }, { lines: ['b', 'a'] }), false);
  assert.equal(jsonSemanticallyEqual({ totalUsd: '12.50' }, { totalUsd: '12.51' }), false);
});
