import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { jsonSemanticallyEqual } from './canonical-json.mjs';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const FIXTURE_FILE = __ENV.FIXTURE_FILE || './generated/b0-fixture.json';
const PROFILE = __ENV.PROFILE || 'smoke';
const WORKLOAD = __ENV.WORKLOAD || 'combined';
const RATE = positiveInteger('RATE', __ENV.RATE || '1');
const PREALLOCATED_VUS = positiveInteger('PREALLOCATED_VUS', __ENV.PREALLOCATED_VUS || String(Math.max(2, RATE)));
const MAX_VUS = positiveInteger('MAX_VUS', __ENV.MAX_VUS || String(Math.max(2, RATE * 2)));
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '2m';
const MEASUREMENT_DURATION = __ENV.MEASUREMENT_DURATION || '10m';
const CALIBRATION_DURATION = __ENV.CALIBRATION_DURATION || '2m';
const CHECKOUT_PERCENT = boundedInteger('CHECKOUT_PERCENT', __ENV.CHECKOUT_PERCENT || '20', 1, 99);
const RUN_ID = __ENV.RUN_ID || 'b0-unidentified';
const RESULTS_DIR = __ENV.RESULTS_DIR || '.';

const fixture = JSON.parse(open(FIXTURE_FILE));

const unexpectedFailures = new Rate('b0_unexpected_failure_rate');
const expectedConflicts = new Counter('b0_expected_conflicts');
const invariantFailures = new Counter('b0_invariant_failures');
const timeouts = new Counter('b0_timeouts');
const checkoutJourneyDuration = new Trend('b0_checkout_journey_duration', true);

const operationTrends = {
  listCategories_default: new Trend('b0_list_categories_default', true),
  listProducts_unfiltered: new Trend('b0_list_products_unfiltered', true),
  listProducts_category_filter: new Trend('b0_list_products_category_filter', true),
  listProducts_common_search: new Trend('b0_list_products_common_search', true),
  listProducts_rare_search: new Trend('b0_list_products_rare_search', true),
  listProducts_no_result_search: new Trend('b0_list_products_no_result_search', true),
  listProducts_cursor_limit_20: new Trend('b0_list_products_cursor_limit_20', true),
  listProducts_cursor_limit_100: new Trend('b0_list_products_cursor_limit_100', true),
  getProduct_default: new Trend('b0_get_product_default', true),
  placeCodOrder_two_seller: new Trend('b0_place_cod_order_two_seller', true),
  getOwnOrder_after_checkout: new Trend('b0_get_own_order_after_checkout', true),
  placeCodOrder_same_key_same_body: new Trend('b0_place_cod_order_same_key_same_body', true),
  placeCodOrder_same_key_changed_body_409: new Trend('b0_place_cod_order_same_key_changed_body_409', true),
};

const previousSignatures = {};

export const options = {
  discardResponseBodies: false,
  scenarios: scenarios(),
  thresholds: {
    checks: ['rate==1'],
    b0_invariant_failures: ['count==0'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['count', 'avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  validateFixture();
  if (WORKLOAD === 'discovery') return { workers: [] };

  const workers = fixture.workers.slice(0, MAX_VUS).map((worker, index) => {
    if (worker.token) return { ...worker, token: worker.token };
    const response = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
      login: worker.login,
      password: worker.password,
    }), requestParams('setup', 'logIn', 'fixture_auth', null, true));
    const ok = check(response, {
      [`fixture worker ${index + 1} login returned 200`]: (r) => r.status === 200,
      [`fixture worker ${index + 1} login returned a token`]: (r) => Boolean(json(r)?.accessToken),
    });
    if (!ok) exec.test.abort(`Fixture login failed for worker ${index + 1}; HTTP ${response.status}`);
    const body = response.json();
    const requiredLifetime = PROFILE === 'baseline'
      ? durationSeconds(WARMUP_DURATION) + durationSeconds(MEASUREMENT_DURATION) + 60
      : durationSeconds(CALIBRATION_DURATION) + 60;
    if (body.expiresInSeconds < requiredLifetime) {
      exec.test.abort(`Worker ${index + 1} JWT lifetime ${body.expiresInSeconds}s is below required ${requiredLifetime}s`);
    }
    return { ...worker, password: undefined, token: body.accessToken };
  });
  return { workers };
}

export default function (data) {
  const stage = currentStage();
  if (WORKLOAD === 'discovery') return discoveryJourney(stage);
  if (WORKLOAD === 'checkout') return checkoutJourney(data, stage);
  if (PROFILE === 'smoke') {
    if (exec.scenario.iterationInTest === 0) return discoveryJourney(stage);
    return checkoutJourney(data, stage);
  }
  if (((exec.scenario.iterationInTest * 37) % 100) < CHECKOUT_PERCENT) return checkoutJourney(data, stage);
  return discoveryJourney(stage);
}

export function handleSummary(data) {
  return {
    [`${RESULTS_DIR}/summary.json`]: JSON.stringify(data, null, 2),
    stdout: `B0 summary written to ${RESULTS_DIR}/summary.json\n`,
  };
}

function scenarios() {
  if (PROFILE === 'smoke') {
    return { smoke: { executor: 'shared-iterations', vus: 1, iterations: WORKLOAD === 'combined' ? 2 : 1, maxDuration: '2m' } };
  }
  if (PROFILE === 'calibration') return { calibration: arrivalScenario('0s', CALIBRATION_DURATION) };
  if (PROFILE === 'baseline') {
    const totalSeconds = durationSeconds(WARMUP_DURATION) + durationSeconds(MEASUREMENT_DURATION);
    return { baseline: arrivalScenario('0s', `${totalSeconds}s`) };
  }
  throw new Error(`PROFILE must be smoke, calibration, or baseline; got ${PROFILE}`);
}

function arrivalScenario(startTime, duration) {
  return {
    executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration, startTime,
    preAllocatedVUs: PREALLOCATED_VUS, maxVUs: MAX_VUS,
  };
}

function discoveryJourney(stage) {
  const publicFixture = fixture.public;
  listPage('/categories?limit=20', 'listCategories', 'default', stage);
  listPage('/products?limit=20', 'listProducts', 'unfiltered', stage);
  listPage(`/products?categoryId=${encodeURIComponent(publicFixture.categoryId)}&limit=20`, 'listProducts', 'category_filter', stage);
  listPage(`/products?q=${encodeURIComponent(publicFixture.commonSearch)}&limit=20`, 'listProducts', 'common_search', stage);
  listPage(`/products?q=${encodeURIComponent(publicFixture.rareSearch)}&limit=20`, 'listProducts', 'rare_search', stage);
  const empty = listPage(`/products?q=${encodeURIComponent(publicFixture.noResultSearch)}&limit=20`, 'listProducts', 'no_result_search', stage);
  assertInvariant('no-result search is empty', empty?.items?.length === 0);
  cursorPages(20, stage);
  cursorPages(100, stage);
  const response = timedRequest('GET', `/products/${publicFixture.productId}`, null, 'BM-01', 'getProduct', 'default', stage);
  assertResponse(response, 200, 'getProduct returns the configured published product');
  assertInvariant('getProduct id matches fixture', json(response)?.id === publicFixture.productId);
}

function listPage(path, operation, variant, stage, page = 'first') {
  const response = timedRequest('GET', path, null, 'BM-01', operation, variant, stage);
  assertResponse(response, 200, `${operation}/${variant} returns 200`);
  const body = json(response);
  assertInvariant(`${operation}/${variant} has page shape`, Array.isArray(body?.items) && typeof body?.hasNext === 'boolean');
  assertNoDuplicateIds(body?.items || [], `${operation}/${variant}`);
  const signature = (body?.items || []).map((item) => item.id).join(',');
  const signatureKey = `${operation}/${variant}/${page}`;
  if (previousSignatures[signatureKey] !== undefined) {
    assertInvariant(`${operation}/${variant} ordering remains stable`, previousSignatures[signatureKey] === signature);
  }
  previousSignatures[signatureKey] = signature;
  return body;
}

function cursorPages(limit, stage) {
  const variant = `cursor_limit_${limit}`;
  const first = listPage(`/products?limit=${limit}`, 'listProducts', variant, stage, 'first');
  assertInvariant(`${variant} fixture has a subsequent page`, first?.hasNext === true && Boolean(first?.nextCursor));
  if (!first?.hasNext || !first?.nextCursor) return;
  const second = listPage(`/products?limit=${limit}&cursor=${encodeURIComponent(first.nextCursor)}`, 'listProducts', variant, stage, 'second');
  const firstIds = new Set((first.items || []).map((item) => item.id));
  assertInvariant(`${variant} pages do not overlap`, (second?.items || []).every((item) => !firstIds.has(item.id)));
}

function checkoutJourney(data, stage) {
  const workerIndex = exec.vu.idInTest - 1;
  const worker = data.workers[workerIndex];
  if (!worker) exec.test.abort(`VU ${exec.vu.idInTest} has no exclusive fixture worker; provide MAX_VUS=${MAX_VUS} workers`);
  const auth = worker.token;
  const startedAt = Date.now();
  let cart = requestJson('GET', '/cart', null, auth, 'BM-04', 'getOwnCart', 'before_checkout', stage, 200);
  cart = requestJson('PUT', `/cart/lines/${worker.variantIds[0]}`, { quantity: 1, expectedVersion: cart.version }, auth,
    'BM-04', 'setCartLine', 'seller_one', stage, 200);
  cart = requestJson('PUT', `/cart/lines/${worker.variantIds[1]}`, { quantity: 1, expectedVersion: cart.version }, auth,
    'BM-04', 'setCartLine', 'seller_two', stage, 200);
  assertInvariant('checkout cart contains two lines', cart?.lines?.length === 2);

  const key = `${RUN_ID}-${stage}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`.slice(0, 128);
  const orderBody = { addressId: worker.addressId, cartVersion: cart.version, paymentMethod: 'COD' };
  const orderResponse = timedRequest('POST', '/orders', orderBody, 'BM-04', 'placeCodOrder', 'two_seller', stage, auth, key);
  assertResponse(orderResponse, 201, 'placeCodOrder/two_seller returns 201');
  const order = json(orderResponse);
  assertInvariant('checkout creates one order id', Boolean(order?.id));
  assertInvariant('checkout creates two Seller portions', order?.sellerOrders?.length === 2);
  assertInvariant('checkout starts in PLACED', order?.status === 'PLACED');

  const detailResponse = timedRequest('GET', `/orders/${order?.id}`, null, 'BM-04', 'getOwnOrder', 'after_checkout', stage, auth);
  assertResponse(detailResponse, 200, 'getOwnOrder/after_checkout returns 200');
  assertInvariant('getOwnOrder returns created order', json(detailResponse)?.id === order?.id);
  const consumedCart = requestJson('GET', '/cart', null, auth, 'BM-04', 'getOwnCart', 'after_checkout', stage, 200);
  assertInvariant('checkout consumes cart', consumedCart?.lines?.length === 0);

  const replayResponse = timedRequest('POST', '/orders', orderBody, 'BM-05', 'placeCodOrder', 'same_key_same_body', stage, auth, key);
  assertResponse(replayResponse, 201, 'same-key/same-body replay returns 201');
  assertInvariant('replay returns original creation snapshot', jsonSemanticallyEqual(json(replayResponse), order));

  const changedBody = { ...orderBody, cartVersion: orderBody.cartVersion + 1 };
  const conflictResponse = timedRequest('POST', '/orders', changedBody, 'BM-05', 'placeCodOrder', 'same_key_changed_body_409', stage, auth, key);
  const expectedConflict = conflictResponse.status === 409 && json(conflictResponse)?.code === 'IDEMPOTENCY_CONFLICT';
  if (stage !== 'warmup') {
    expectedConflicts.add(expectedConflict ? 1 : 0, tags('BM-05', 'placeCodOrder', 'same_key_changed_body_409', stage));
  }
  recordOutcome(conflictResponse, expectedConflict);
  assertInvariant('same-key/changed-body returns exact idempotency conflict', expectedConflict);
  if (stage === 'measurement') checkoutJourneyDuration.add(Date.now() - startedAt, tags('BM-04', 'checkout_journey', 'two_seller', stage));
}

function requestJson(method, path, body, token, scenario, operation, variant, stage, expectedStatus) {
  const response = timedRequest(method, path, body, scenario, operation, variant, stage, token);
  assertResponse(response, expectedStatus, `${operation}/${variant} returns ${expectedStatus}`);
  return json(response);
}

function timedRequest(method, path, body, scenario, operation, variant, stage, token = null, idempotencyKey = null) {
  const response = http.request(method, `${BASE_URL}${path}`, body === null ? null : JSON.stringify(body),
    requestParams(scenario, operation, variant, token, body !== null, idempotencyKey, stage));
  if (stage === 'measurement' || stage === 'calibration' || stage === 'smoke') {
    operationTrends[`${operation}_${variant}`]?.add(response.timings.duration, tags(scenario, operation, variant, stage));
  }
  return response;
}

function requestParams(scenario, operation, variant, token, jsonBody, idempotencyKey = null, stage = 'setup') {
  const headers = {};
  if (jsonBody) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  return { headers, tags: tags(scenario, operation, variant, stage), timeout: __ENV.REQUEST_TIMEOUT || '5s', responseType: 'text' };
}

function tags(scenario, operation, variant, stage) {
  return { phase: 'B0', benchmark_scenario: scenario, operation, variant, stage, run_id: RUN_ID };
}

function assertResponse(response, expectedStatus, name) {
  const ok = response.status === expectedStatus;
  check(response, { [name]: () => ok });
  recordOutcome(response, ok);
  if (!ok) invariantFailures.add(1, runTags());
}

function assertInvariant(name, condition) {
  const ok = Boolean(condition);
  check(null, { [name]: () => ok });
  if (!ok) invariantFailures.add(1, { phase: 'B0', run_id: RUN_ID });
}

function recordOutcome(response, expected) {
  if (currentStage() === 'warmup') return;
  if (response.status === 0) timeouts.add(1, runTags());
  unexpectedFailures.add(!expected, runTags());
}

function runTags() {
  return { phase: 'B0', stage: currentStage(), run_id: RUN_ID };
}

function assertNoDuplicateIds(items, name) {
  const ids = items.map((item) => item.id);
  assertInvariant(`${name} has no duplicate ids`, ids.length === new Set(ids).size);
}

function json(response) {
  try { return response.json(); } catch (_) { return null; }
}

function validateFixture() {
  if (!['discovery', 'checkout', 'combined'].includes(WORKLOAD)) {
    exec.test.abort(`WORKLOAD must be discovery, checkout, or combined; got ${WORKLOAD}`);
  }
  const publicFixture = fixture.public || {};
  for (const field of ['categoryId', 'productId', 'commonSearch', 'rareSearch', 'noResultSearch']) {
    if (!publicFixture[field]) exec.test.abort(`Fixture public.${field} is required`);
  }
  if (WORKLOAD === 'discovery') return;
  if (!Array.isArray(fixture.workers) || fixture.workers.length < MAX_VUS) {
    exec.test.abort(`Fixture needs at least MAX_VUS=${MAX_VUS} exclusive workers; got ${fixture.workers?.length || 0}`);
  }
  fixture.workers.slice(0, MAX_VUS).forEach((worker, index) => {
    if ((!worker.token && (!worker.login || !worker.password)) || !worker.addressId || worker.variantIds?.length !== 2) {
      exec.test.abort(`Worker ${index + 1} needs token or login/password, addressId, and exactly two cross-Seller variantIds`);
    }
  });
}

function positiveInteger(name, value) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) throw new Error(`${name} must be a positive integer`);
  return parsed;
}

function boundedInteger(name, value, minimum, maximum) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} to ${maximum}`);
  }
  return parsed;
}

function durationSeconds(value) {
  const match = /^(\d+)(s|m|h)$/.exec(value);
  if (!match) throw new Error(`Duration must use integer s, m, or h units; got ${value}`);
  return Number(match[1]) * ({ s: 1, m: 60, h: 3600 }[match[2]]);
}

function currentStage() {
  if (PROFILE !== 'baseline') return exec.scenario.name;
  const elapsedMilliseconds = Date.now() - exec.scenario.startTime;
  return elapsedMilliseconds < durationSeconds(WARMUP_DURATION) * 1000 ? 'warmup' : 'measurement';
}
