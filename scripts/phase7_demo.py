#!/usr/bin/env python3
"""Run the PB-019 HTTP demo against fresh, disposable local PostgreSQL."""

import argparse
import datetime
import http.cookiejar
import json
import os
import pathlib
import re
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR = ROOT / "build/libs/marketplace-backend-0.1.0-SNAPSHOT.jar"
CONTRACT = yaml.safe_load((ROOT.parent / "docs-minimal/SolutionArchitecture/api-contract/openapi.yaml").read_text())
OPERATIONS = {
    operation["operationId"]: operation
    for path in CONTRACT["paths"].values()
    for method, operation in path.items()
    if method in {"get", "post", "put", "patch", "delete"}
}
ORIGIN = "http://localhost:3000"
SELLER_B_ACCOUNT = "88888888-8888-8888-8888-888888888888"
SELLER_B_SHOP = "99999999-9999-9999-9999-999999999999"
SELLER_B_PRODUCT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
SELLER_B_VARIANT = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
SELLER_A_SHOP = "33333333-3333-3333-3333-333333333333"
TARGET_OPS = {
    "logIn", "registerMember", "listCategories", "listProducts", "getProduct",
    "getOwnProduct", "updateOwnStock", "getOwnCart", "setCartLine", "removeCartLine",
    "placeCodOrder", "listOwnOrders", "getOwnOrder", "cancelUnpaidOrder",
    "listOwnSellerOrders", "markOwnOrderPaid", "listOwnCheckoutAddresses",
    "createOwnCheckoutAddress",
}
EVIDENCE_CASES = {
    "lastUnit": "twoMembersRacingForLastUnitCommitOnlyOneOrder",
    "sameKey": "concurrentIdenticalKeyReturnsOneOriginalOrderAndOneStockDecrement",
    "cancelWins": "cancelWinsRootLockBeforePayment",
    "paymentWins": "paymentWinsRootLockBeforeCancel",
    "twoSellerPayment": "twoSellersMarkConcurrentlyWithoutLostPayment",
    "checkoutAuditRollback": "lateAuditFailureRollsBackStockOrderCartAndKey",
    "paymentAuditRollback": "auditFailureRollsBackPayment",
}


def run(args, *, input_text=None, env=None):
    result = subprocess.run(args, input=input_text, text=True, capture_output=True,
                            env=env, timeout=120, check=False)
    if result.returncode:
        raise RuntimeError(f"{args[0]} {args[1]} failed: {result.stderr[-500:]}")
    return result.stdout.strip()


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def key_pair():
    private_pem = subprocess.run(
        ["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048"],
        capture_output=True, check=True).stdout
    public_pem = subprocess.run(["openssl", "pkey", "-pubout"], input=private_pem,
                                capture_output=True, check=True).stdout

    def der(pem):
        return b"".join(line for line in pem.splitlines() if not line.startswith(b"-----")).decode()

    return der(private_pem), der(public_pem)


def password_hash(password):
    hashed = run(["htpasswd", "-niBC", "10", "demo"], input_text=password)
    return hashed.split(":", 1)[1]


def request(base, method, path, *, body=None, token=None, jar=None, extra=None):
    headers = dict(extra or {})
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    if data is not None:
        headers["Content-Type"] = "application/json"
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar)) if jar is not None else urllib.request.build_opener()
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        response = opener.open(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    value = json.loads(raw) if raw else None
    return response.status, value


def resolve(schema):
    while "$ref" in schema:
        value = CONTRACT
        for part in schema["$ref"].lstrip("#/").split("/"):
            value = value[part]
        schema = value
    return schema


def validate_schema(value, raw_schema, where):
    schema = resolve(raw_schema)
    for part in schema.get("allOf", []):
        validate_schema(value, part, where)
    if "oneOf" in schema:
        matches = 0
        for part in schema["oneOf"]:
            try:
                validate_schema(value, part, where)
                matches += 1
            except AssertionError:
                pass
        require(matches == 1, f"{where}: expected one schema match, got {matches}")
    types = schema.get("type", [])
    if isinstance(types, str):
        types = [types]
    valid_type = {"object": lambda x: isinstance(x, dict),
                  "array": lambda x: isinstance(x, list),
                  "string": lambda x: isinstance(x, str),
                  "integer": lambda x: isinstance(x, int) and not isinstance(x, bool),
                  "number": lambda x: isinstance(x, (int, float)) and not isinstance(x, bool),
                  "boolean": lambda x: isinstance(x, bool), "null": lambda x: x is None}
    if types:
        require(any(valid_type[kind](value) for kind in types), f"{where}: wrong JSON type")
    if "const" in schema:
        require(value == schema["const"], f"{where}: const mismatch")
    if "enum" in schema:
        require(value in schema["enum"], f"{where}: enum mismatch")
    if isinstance(value, dict):
        for field in schema.get("required", []):
            require(field in value, f"{where}: missing {field}")
        properties = schema.get("properties", {})
        for field, item in value.items():
            if field in properties:
                validate_schema(item, properties[field], where + "." + field)
            else:
                additional = schema.get("additionalProperties", True)
                require(additional is not False, f"{where}: unexpected {field}")
                if isinstance(additional, dict):
                    validate_schema(item, additional, where + "." + field)
        if "minProperties" in schema:
            require(len(value) >= schema["minProperties"], f"{where}: too few fields")
    if isinstance(value, list):
        if "minItems" in schema:
            require(len(value) >= schema["minItems"], f"{where}: too few items")
        if "maxItems" in schema:
            require(len(value) <= schema["maxItems"], f"{where}: too many items")
        for index, item in enumerate(value):
            validate_schema(item, schema.get("items", {}), f"{where}[{index}]")
    if isinstance(value, str):
        if "minLength" in schema:
            require(len(value) >= schema["minLength"], f"{where}: too short")
        if "maxLength" in schema:
            require(len(value) <= schema["maxLength"], f"{where}: too long")
        if "pattern" in schema:
            require(re.search(schema["pattern"], value) is not None, f"{where}: pattern mismatch")
        if schema.get("format") == "uuid":
            uuid.UUID(value)
        if schema.get("format") == "date-time":
            datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))
        if schema.get("format") == "uri":
            require(":" in value, f"{where}: invalid URI")
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema:
            require(value >= schema["minimum"], f"{where}: below minimum")
        if "maximum" in schema:
            require(value <= schema["maximum"], f"{where}: above maximum")


def assert_http(base, method, path, status, *, operation=None, body=None, token=None, jar=None, extra=None, seen=None):
    actual, value = request(base, method, path, body=body, token=token, jar=jar, extra=extra)
    if actual != status:
        code = value.get("code") if isinstance(value, dict) else None
        raise AssertionError(f"{operation or path}: expected HTTP {status}, got {actual} ({code})")
    if status >= 400 and not (isinstance(value, dict) and value.get("code")):
        raise AssertionError(f"{operation or path}: missing problem code")
    if operation:
        definition = OPERATIONS[operation]
        response = resolve(definition["responses"][str(status)])
        content = response.get("content", {})
        if content:
            schema = next(iter(content.values()))["schema"]
            validate_schema(value, schema, operation + f" HTTP {status}")
        else:
            require(value is None, f"{operation}: unexpected response body")
        if status >= 400:
            require(value["code"] in definition["x-error-codes"][str(status)],
                    f"{operation}: undocumented error code {value['code']}")
        seen[operation].append(status)
    return value


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def seed_second_seller(container, hashed):
    # The freshly migrated DB is disposable and uniquely owned by this process.
    # This is fixture provisioning; all demonstrated mutations use HTTP.
    sql = f"""
INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name)
VALUES ('{SELLER_B_ACCOUNT}','synthetic.seller-b@example.test','{hashed}','SELLER','ACTIVE','Synthetic Seller B');
INSERT INTO marketplace.seller_shop(id,account_id,name,status)
VALUES ('{SELLER_B_SHOP}','{SELLER_B_ACCOUNT}','Synthetic Seller B Shop','ACTIVE');
INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published)
VALUES ('{SELLER_B_PRODUCT}','{SELLER_B_SHOP}','55555555-5555-5555-5555-555555555555',
        'Synthetic Kotlin Guide','Second synthetic Seller product',true);
INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd)
VALUES ('{SELLER_B_VARIANT}','{SELLER_B_PRODUCT}','SYNTHETIC-KOTLIN-GUIDE','Paperback',9.25);
INSERT INTO marketplace.stock(variant_id,on_hand) VALUES ('{SELLER_B_VARIANT}',7);
"""
    run(["docker", "exec", "-i", container, "psql", "-v", "ON_ERROR_STOP=1", "-U",
         "marketplace_dev", "-d", "marketplace"], input_text=sql)


def wait_for_db(container):
    for _ in range(60):
        result = subprocess.run(["docker", "exec", container, "pg_isready", "-U",
                                 "marketplace_dev", "-d", "marketplace"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode == 0:
            return
        time.sleep(0.5)
    raise RuntimeError("disposable PostgreSQL did not become ready")


def wait_for_api(base, process):
    for _ in range(120):
        if process.poll() is not None:
            raise RuntimeError("API exited before readiness")
        try:
            status, _ = request(base, "GET", "/categories")
            if status == 200:
                return
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(0.5)
    raise RuntimeError("API did not become ready")


def exercise(base, seller_password):
    seen = {name: [] for name in TARGET_OPS}
    guest = http.cookiejar.CookieJar()
    categories = assert_http(base, "GET", "/categories", 200, operation="listCategories", seen=seen)
    require(categories["items"], "category fixture missing")
    products = assert_http(base, "GET", "/products", 200, operation="listProducts", seen=seen)
    require(len(products["items"]) == 2, "expected two published, active Seller products")
    by_shop = {item["sellerId"]: item for item in products["items"]}
    require(set(by_shop) == {SELLER_A_SHOP, SELLER_B_SHOP}, "SellerShop identities differ from fixture")
    product_a = assert_http(base, "GET", "/products/" + by_shop[SELLER_A_SHOP]["id"], 200,
                            operation="getProduct", seen=seen)
    product_b = assert_http(base, "GET", "/products/" + by_shop[SELLER_B_SHOP]["id"], 200,
                            operation="getProduct", seen=seen)
    variant_a = product_a["variants"][0]["id"]
    variant_b = product_b["variants"][0]["id"]
    stock_a_before = product_a["variants"][0]["stock"]["version"]

    cart = assert_http(base, "GET", "/cart", 200, operation="getOwnCart", jar=guest, seen=seen)
    require(cart["lines"] == [], "Guest cart must start empty")
    for variant in (variant_a, variant_b):
        cart = assert_http(base, "PUT", "/cart/lines/" + variant, 200,
                           operation="setCartLine", body={"quantity": 1, "expectedVersion": cart["version"]},
                           jar=guest, extra={"Origin": ORIGIN}, seen=seen)
    assert_http(base, "DELETE", "/cart/lines/" + variant_b + f"?expectedVersion={cart['version']}",
                204, operation="removeCartLine", jar=guest, extra={"Origin": ORIGIN}, seen=seen)
    cart = assert_http(base, "GET", "/cart", 200, operation="getOwnCart", jar=guest, seen=seen)
    cart = assert_http(base, "PUT", "/cart/lines/" + variant_b, 200,
                       operation="setCartLine", body={"quantity": 1, "expectedVersion": cart["version"]},
                       jar=guest, extra={"Origin": ORIGIN}, seen=seen)
    require(len(cart["lines"]) == 2, "Guest cart needs two Seller lines")

    member_password = secrets.token_urlsafe(20)
    member_login = "demo-" + uuid.uuid4().hex + "@example.test"
    account = assert_http(base, "POST", "/accounts", 201, operation="registerMember",
                          body={"login": member_login, "password": member_password,
                                "displayName": "Synthetic Member A"}, seen=seen)
    require(account["role"] == "MEMBER" and account.get("id"), "registration response shape")
    member = assert_http(base, "POST", "/auth/login", 200, operation="logIn",
                         body={"login": member_login, "password": member_password}, jar=guest,
                         extra={"Origin": ORIGIN}, seen=seen)
    require(member["cartMerge"]["merged"] and member["accessToken"], "Guest merge/token missing")
    member_token = member["accessToken"]
    cart = assert_http(base, "GET", "/cart", 200, operation="getOwnCart", token=member_token, seen=seen)
    require(len(cart["lines"]) == 2, "Member cart lost merged lines")
    replay_login = assert_http(base, "POST", "/auth/login", 200, operation="logIn",
                               body={"login": member_login, "password": member_password},
                               jar=guest, extra={"Origin": ORIGIN}, seen=seen)
    require(not replay_login["cartMerge"]["merged"], "Guest source merged twice")

    addresses = assert_http(base, "GET", "/me/addresses", 200,
                            operation="listOwnCheckoutAddresses", token=member_token, seen=seen)
    require(addresses["items"] == [], "fresh Member has an address")
    address = assert_http(base, "POST", "/me/addresses", 201,
                          operation="createOwnCheckoutAddress", token=member_token,
                          body={"recipient": "Synthetic Member A", "addressLine": "1 Demo Street",
                                "locality": "Demo City", "postalCode": "10000", "country": "US"}, seen=seen)
    require(address.get("id"), "address ID missing")
    addresses = assert_http(base, "GET", "/me/addresses", 200,
                            operation="listOwnCheckoutAddresses", token=member_token, seen=seen)
    require(any(item["id"] == address["id"] for item in addresses["items"]), "owned address missing")

    seller_tokens = {}
    for shop, login in ((SELLER_A_SHOP, "synthetic.seller@example.test"),
                        (SELLER_B_SHOP, "synthetic.seller-b@example.test")):
        logged = assert_http(base, "POST", "/auth/login", 200, operation="logIn",
                             body={"login": login, "password": seller_password}, seen=seen)
        require(logged["account"]["role"] == "SELLER", "Seller login role")
        seller_tokens[shop] = logged["accessToken"]
    own_product = assert_http(base, "GET", "/seller/products/" + product_a["id"], 200,
                              operation="getOwnProduct", token=seller_tokens[SELLER_A_SHOP], seen=seen)
    require(own_product["sellerId"] == SELLER_A_SHOP, "Seller product owner")

    body = {"addressId": address["id"], "cartVersion": cart["version"], "paymentMethod": "COD"}
    key = uuid.uuid4().hex
    placed = assert_http(base, "POST", "/orders", 201, operation="placeCodOrder",
                         body=body, token=member_token, extra={"Idempotency-Key": key}, seen=seen)
    require(len(placed["sellerOrders"]) == 2 and placed["paymentStatus"] == "PENDING_PAYMENT",
            "two-Seller pending order shape")
    order_id = placed["id"]
    replay = assert_http(base, "POST", "/orders", 201, operation="placeCodOrder",
                         body=body, token=member_token, extra={"Idempotency-Key": key}, seen=seen)
    require(replay == placed, "same-key replay differs from original snapshot")
    replay_stock = assert_http(base, "GET", "/products/" + product_a["id"], 200,
                               operation="getProduct", seen=seen)["variants"][0]["stock"]
    require(replay_stock["version"] == stock_a_before + 1, "same-key replay decremented stock twice")
    conflict = assert_http(base, "POST", "/orders", 409, operation="placeCodOrder",
                           body={**body, "cartVersion": body["cartVersion"] + 1}, token=member_token,
                           extra={"Idempotency-Key": key}, seen=seen)
    require(conflict["code"] == "IDEMPOTENCY_CONFLICT", "changed-body conflict code")
    detail = assert_http(base, "GET", "/orders/" + order_id, 200,
                         operation="getOwnOrder", token=member_token, seen=seen)
    require(detail["id"] == order_id, "owner detail ID")
    history = assert_http(base, "GET", "/orders", 200,
                          operation="listOwnOrders", token=member_token, seen=seen)
    require([item["id"] for item in history["items"]] == [order_id], "replay created another order")

    portions = {item["sellerId"]: item["id"] for item in placed["sellerOrders"]}
    for shop in (SELLER_A_SHOP, SELLER_B_SHOP):
        page = assert_http(base, "GET", "/seller/orders", 200,
                           operation="listOwnSellerOrders", token=seller_tokens[shop], seen=seen)
        require(any(item["id"] == portions[shop] for item in page["items"]), "Seller owned portion missing")
        require(all(item["sellerId"] == shop for item in page["items"]), "Seller page leaked foreign portion")
    foreign = assert_http(base, "POST", "/seller/orders/" + portions[SELLER_A_SHOP] + "/mark-paid", 404,
                          operation="markOwnOrderPaid", token=seller_tokens[SELLER_B_SHOP], seen=seen)
    require(foreign["code"] == "RESOURCE_NOT_FOUND", "foreign Seller error")
    for index, shop in enumerate((SELLER_A_SHOP, SELLER_B_SHOP)):
        paid = assert_http(base, "POST", "/seller/orders/" + portions[shop] + "/mark-paid", 200,
                           operation="markOwnOrderPaid", token=seller_tokens[shop], seen=seen)
        require(paid["sellerId"] == shop and paid["paymentStatus"] == "PAID", "owned paid response")
        detail = assert_http(base, "GET", "/orders/" + order_id, 200,
                             operation="getOwnOrder", token=member_token, seen=seen)
        require(detail["paymentStatus"] == ("PENDING_PAYMENT" if index == 0 else "PAID"),
                "aggregate payment projection")
    duplicate = assert_http(base, "POST", "/seller/orders/" + portions[SELLER_A_SHOP] + "/mark-paid", 409,
                            operation="markOwnOrderPaid", token=seller_tokens[SELLER_A_SHOP], seen=seen)
    require(duplicate["code"] == "ILLEGAL_ORDER_STATE", "duplicate payment code")
    paid_cancel = assert_http(base, "POST", "/orders/" + order_id + "/cancellation", 409,
                              operation="cancelUnpaidOrder", token=member_token, seen=seen)
    require(paid_cancel["code"] == "ILLEGAL_ORDER_STATE", "paid cancellation code")

    cart = assert_http(base, "GET", "/cart", 200, operation="getOwnCart", token=member_token, seen=seen)
    for variant in (variant_a, variant_b):
        cart = assert_http(base, "PUT", "/cart/lines/" + variant, 200,
                           operation="setCartLine", token=member_token,
                           body={"quantity": 1, "expectedVersion": cart["version"]}, seen=seen)
    pending = assert_http(base, "POST", "/orders", 201, operation="placeCodOrder",
                          body={**body, "cartVersion": cart["version"]}, token=member_token,
                          extra={"Idempotency-Key": uuid.uuid4().hex}, seen=seen)
    cancelled = assert_http(base, "POST", "/orders/" + pending["id"] + "/cancellation", 200,
                            operation="cancelUnpaidOrder", token=member_token, seen=seen)
    again = assert_http(base, "POST", "/orders/" + pending["id"] + "/cancellation", 200,
                        operation="cancelUnpaidOrder", token=member_token, seen=seen)
    require(cancelled == again and cancelled["status"] == "CANCELLED", "repeat cancel changed snapshot")
    restored = assert_http(base, "GET", "/products/" + product_a["id"], 200,
                           operation="getProduct", seen=seen)["variants"][0]["stock"]
    require(restored["version"] == stock_a_before + 3, "cancel/retry stock version is incorrect")

    member_b_password = secrets.token_urlsafe(20)
    member_b_login = "demo-" + uuid.uuid4().hex + "@example.test"
    assert_http(base, "POST", "/accounts", 201, operation="registerMember",
                body={"login": member_b_login, "password": member_b_password,
                      "displayName": "Synthetic Member B"}, seen=seen)
    member_b = assert_http(base, "POST", "/auth/login", 200, operation="logIn",
                           body={"login": member_b_login, "password": member_b_password}, seen=seen)
    hidden = assert_http(base, "GET", "/orders/" + order_id, 404,
                         operation="getOwnOrder", token=member_b["accessToken"], seen=seen)
    require(hidden["code"] == "RESOURCE_NOT_FOUND", "foreign Member order code")
    stock = assert_http(base, "GET", "/seller/products/" + product_a["id"], 200,
                        operation="getOwnProduct", token=seller_tokens[SELLER_A_SHOP], seen=seen)
    current_stock = stock["variants"][0]["stock"]
    require(current_stock["version"] > stock_a_before, "checkout/cancel did not advance stock version")
    stale = assert_http(base, "PATCH", "/seller/stock/" + variant_a, 409,
                        operation="updateOwnStock", token=seller_tokens[SELLER_A_SHOP],
                        body={"onHand": current_stock["onHand"], "expectedVersion": stock_a_before}, seen=seen)
    require(stale["code"] == "STALE_VERSION", "stale stock code")
    updated = assert_http(base, "PATCH", "/seller/stock/" + variant_a, 200,
                          operation="updateOwnStock", token=seller_tokens[SELLER_A_SHOP],
                          body={"onHand": current_stock["onHand"],
                                "expectedVersion": current_stock["version"]}, seen=seen)
    require(updated["version"] == current_stock["version"] + 1, "stock write version")
    require(all(seen.values()), "missing operation: " + str([name for name, calls in seen.items() if not calls]))
    return {"operations": {name: seen[name] for name in sorted(seen)},
            "assertions": sum(map(len, seen.values())), "paidOrder": order_id,
            "cancelledOrder": pending["id"], "stockVersion": updated["version"]}


def one_run(index):
    container = "marketplace-phase7-" + uuid.uuid4().hex[:12]
    db_password = secrets.token_urlsafe(24)
    seller_password = secrets.token_urlsafe(20)
    hashed = password_hash(seller_password)
    private_key, public_key = key_pair()
    port = free_port()
    db_env = {**os.environ, "POSTGRES_PASSWORD": db_password}
    process = None
    created = False
    try:
        run(["docker", "run", "--rm", "-d", "--name", container,
             "--label", "marketplace.phase7.disposable=true", "-e", "POSTGRES_DB=marketplace",
             "-e", "POSTGRES_USER=marketplace_dev", "-e", "POSTGRES_PASSWORD",
             "-p", "127.0.0.1::5432", "postgres:18"], env=db_env)
        created = True
        wait_for_db(container)
        mapping = run(["docker", "port", container, "5432/tcp"])
        db_port = int(mapping.rsplit(":", 1)[1])
        app_env = {**os.environ, "SPRING_PROFILES_ACTIVE": "dev", "DEV_FIXTURES_ENABLED": "true",
                   "DEV_MEMBER_PASSWORD_HASH": hashed, "DEV_SELLER_PASSWORD_HASH": hashed,
                   "DEV_ADMIN_PASSWORD_HASH": hashed, "DB_URL": f"jdbc:postgresql://127.0.0.1:{db_port}/marketplace",
                   "DB_RUNTIME_USER": "marketplace_dev", "DB_RUNTIME_PASSWORD": db_password,
                   "DB_MIGRATION_USER": "marketplace_dev", "DB_MIGRATION_PASSWORD": db_password,
                   "JWT_PRIVATE_KEY": private_key, "JWT_PUBLIC_KEY": public_key,
                   "CURSOR_MAC_SECRET": secrets.token_urlsafe(32), "SERVER_PORT": str(port)}
        process = subprocess.Popen(["java", "-jar", str(JAR)], cwd=ROOT, env=app_env,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        base = f"http://127.0.0.1:{port}"
        wait_for_api(base, process)
        seed_second_seller(container, hashed)
        result = exercise(base, seller_password)
        result["run"] = index
        result["database"] = "fresh disposable PostgreSQL 18"
        return result
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if created:
            subprocess.run(["docker", "rm", "-f", container], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, check=False)


def test_report():
    totals = {}
    passed_cases = set()
    latest_source = max(path.stat().st_mtime for path in ROOT.glob("src/**/*.java"))
    latest_source = max(latest_source, (ROOT / "build.gradle.kts").stat().st_mtime)
    for suite in ("test", "integrationTest", "checkoutRed"):
        directory = ROOT / "build/test-results" / suite
        files = sorted(directory.glob("TEST-*.xml"))
        require(files, f"{suite} has no XML reports; run ./gradlew check --rerun-tasks")
        require(all(file.stat().st_mtime >= latest_source for file in files),
                f"{suite} XML predates source/build changes; rerun ./gradlew check --rerun-tasks")
        counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
        for file in files:
            root = ET.parse(file).getroot()
            for key in counts:
                counts[key] += int(root.attrib.get(key, "0"))
            for case in root.iter("testcase"):
                if not any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                    passed_cases.add(case.attrib["name"].split("(", 1)[0])
        require(counts["tests"] > 0 and not any(counts[key] for key in ("failures", "errors", "skipped")),
                f"{suite} test report is not fully green: {counts}")
        totals[suite] = counts
    missing = [name for name in EVIDENCE_CASES.values() if name not in passed_cases]
    require(not missing, f"race/rollback evidence missing: {missing}")
    return {"suites": totals, "raceRollback": {name: "PASS" for name in EVIDENCE_CASES}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=int, default=2, choices=(1, 2))
    parser.add_argument("--verify-reports", action="store_true",
                        help="require current local JUnit reports and show race/rollback evidence")
    args = parser.parse_args()
    if not JAR.is_file():
        raise SystemExit("Build the API first: ./gradlew bootJar")
    revision = run(["git", "rev-parse", "HEAD"])
    results = []
    for index in range(1, args.runs + 1):
        result = one_run(index)
        results.append(result)
        print(f"Run {index}: PASS, {len(result['operations'])} operations, {result['assertions']} HTTP assertions",
              flush=True)
    report = {"status": "PASS", "revision": revision, "runs": results}
    if args.verify_reports:
        report["tests"] = test_report()
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Phase 7 FAIL: {error}", file=sys.stderr)
        sys.exit(1)
