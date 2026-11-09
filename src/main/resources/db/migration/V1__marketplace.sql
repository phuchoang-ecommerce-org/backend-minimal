CREATE SCHEMA IF NOT EXISTS marketplace;

CREATE TABLE marketplace.account (
  id uuid NOT NULL,
  normalized_login text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  role text NOT NULL,
  status text NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  CHECK (role IN ('MEMBER','SELLER','ADMIN')),
  CHECK (normalized_login = lower(btrim(normalized_login)) AND normalized_login <> '')
);

CREATE TABLE marketplace.seller_shop (
  id uuid NOT NULL,
  account_id uuid NOT NULL UNIQUE REFERENCES marketplace.account(id),
  name text NOT NULL,
  status text NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE TABLE marketplace.address (
  id uuid NOT NULL,
  member_id uuid NOT NULL REFERENCES marketplace.account(id),
  recipient text NOT NULL,
  address_line text NOT NULL,
  locality text NOT NULL,
  postal_code text NOT NULL,
  country text NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id)
);

CREATE TABLE marketplace.category (
  id uuid NOT NULL,
  name text NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id)
);

CREATE TABLE marketplace.product (
  id uuid NOT NULL,
  seller_id uuid NOT NULL REFERENCES marketplace.seller_shop(id),
  category_id uuid NOT NULL REFERENCES marketplace.category(id),
  title text NOT NULL,
  description text NOT NULL,
  published boolean NOT NULL,
  search_document tsvector GENERATED ALWAYS AS (setweight(to_tsvector('simple', coalesce(title,'')), 'A') || setweight(to_tsvector('simple', coalesce(description,'')), 'B')) STORED,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id)
);

CREATE TABLE marketplace.variant (
  id uuid NOT NULL,
  product_id uuid NOT NULL REFERENCES marketplace.product(id),
  sku text NOT NULL UNIQUE,
  label text NOT NULL,
  price_usd numeric(12,2) NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  CHECK (price_usd >= 0),
  UNIQUE (id, product_id)
);

CREATE TABLE marketplace.product_image (
  id uuid NOT NULL,
  product_id uuid NOT NULL REFERENCES marketplace.product(id),
  mode text NOT NULL,
  source_url text,
  source_name text,
  license text,
  content bytea,
  mime_type text,
  byte_size integer,
  width integer,
  height integer,
  sort_order integer NOT NULL,
  alt_text text NOT NULL,
  PRIMARY KEY (id),
  CHECK (mode IN ('LINK','UPLOAD')),
  CHECK ((mode='LINK' AND source_url IS NOT NULL AND source_url LIKE 'https://%' AND source_name IS NOT NULL AND license IS NOT NULL AND content IS NULL)
       OR (mode='UPLOAD' AND content IS NOT NULL AND source_url IS NULL AND source_name IS NULL AND license IS NULL AND mime_type IS NOT NULL AND mime_type IN ('image/jpeg','image/png','image/webp') AND byte_size IS NOT NULL AND byte_size=octet_length(content) AND byte_size>0 AND width IS NOT NULL AND width>0 AND height IS NOT NULL AND height>0)),
  CHECK (sort_order >= 0)
);

CREATE TABLE marketplace.stock (
  variant_id uuid NOT NULL REFERENCES marketplace.variant(id),
  on_hand integer NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (variant_id),
  CHECK (on_hand >= 0)
);

CREATE TABLE marketplace.cart (
  id uuid NOT NULL,
  member_id uuid REFERENCES marketplace.account(id),
  guest_session_hash text,
  expires_at timestamptz,
  merged_into_cart_id uuid REFERENCES marketplace.cart(id),
  merged_at timestamptz,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  UNIQUE (member_id),
  UNIQUE (guest_session_hash),
  CHECK ((member_id IS NULL) <> (guest_session_hash IS NULL)),
  CHECK (guest_session_hash IS NULL OR expires_at IS NOT NULL),
  CHECK ((merged_into_cart_id IS NULL) = (merged_at IS NULL)),
  CHECK (merged_into_cart_id IS NULL OR (merged_into_cart_id <> id AND guest_session_hash IS NOT NULL))
);

CREATE TABLE marketplace.cart_line (
  cart_id uuid NOT NULL REFERENCES marketplace.cart(id),
  variant_id uuid NOT NULL REFERENCES marketplace.variant(id),
  quantity integer NOT NULL,
  PRIMARY KEY (cart_id, variant_id),
  CHECK (quantity > 0)
);

CREATE TABLE marketplace.customer_order (
  id uuid NOT NULL,
  member_id uuid NOT NULL REFERENCES marketplace.account(id),
  idempotency_key text NOT NULL,
  request_fingerprint text NOT NULL,
  status text NOT NULL,
  total_usd numeric(12,2) NOT NULL,
  currency text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cancelled_at timestamptz,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  UNIQUE (member_id, idempotency_key),
  CHECK (status IN ('PLACED','CANCELLED')),
  CHECK ((status='CANCELLED') = (cancelled_at IS NOT NULL)),
  CHECK (currency='USD'),
  CHECK (total_usd >= 0)
);

CREATE TABLE marketplace.seller_order (
  id uuid NOT NULL,
  customer_order_id uuid NOT NULL REFERENCES marketplace.customer_order(id),
  seller_id uuid NOT NULL REFERENCES marketplace.seller_shop(id),
  payment_status text NOT NULL,
  total_usd numeric(12,2) NOT NULL,
  paid_at timestamptz,
  PRIMARY KEY (id),
  UNIQUE (customer_order_id, seller_id),
  CHECK (payment_status IN ('PENDING_PAYMENT','PAID')),
  CHECK ((payment_status='PAID') = (paid_at IS NOT NULL)),
  CHECK (total_usd >= 0)
);

CREATE TABLE marketplace.order_line (
  id uuid NOT NULL,
  seller_order_id uuid NOT NULL REFERENCES marketplace.seller_order(id),
  variant_id uuid NOT NULL REFERENCES marketplace.variant(id),
  product_id uuid NOT NULL REFERENCES marketplace.product(id),
  product_title_snapshot text NOT NULL,
  sku_snapshot text NOT NULL,
  quantity integer NOT NULL,
  unit_price_usd numeric(12,2) NOT NULL,
  discount_usd numeric(12,2) NOT NULL,
  line_total_usd numeric(12,2) NOT NULL,
  PRIMARY KEY (id),
  CHECK (quantity > 0 AND unit_price_usd >= 0 AND discount_usd >= 0),
  CHECK (discount_usd <= quantity * unit_price_usd),
  CHECK (line_total_usd = quantity * unit_price_usd - discount_usd),
  FOREIGN KEY (variant_id, product_id) REFERENCES marketplace.variant(id, product_id)
);

CREATE TABLE marketplace.order_address_snapshot (
  customer_order_id uuid NOT NULL REFERENCES marketplace.customer_order(id),
  recipient text NOT NULL,
  address_line text NOT NULL,
  locality text NOT NULL,
  postal_code text NOT NULL,
  country text NOT NULL,
  PRIMARY KEY (customer_order_id)
);

CREATE TABLE marketplace.voucher (
  id uuid NOT NULL,
  code text NOT NULL UNIQUE,
  valid_from timestamptz NOT NULL,
  valid_until timestamptz NOT NULL,
  eligibility_rule jsonb NOT NULL,
  discount_rule jsonb NOT NULL,
  usage_limit integer,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  CHECK (valid_from < valid_until),
  CHECK (usage_limit IS NULL OR usage_limit > 0),
  CHECK (jsonb_typeof(eligibility_rule)='object' AND jsonb_typeof(discount_rule)='object')
);

CREATE TABLE marketplace.voucher_redemption (
  id uuid NOT NULL,
  voucher_id uuid NOT NULL REFERENCES marketplace.voucher(id),
  customer_order_id uuid NOT NULL UNIQUE REFERENCES marketplace.customer_order(id),
  discount_usd numeric(12,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CHECK (discount_usd >= 0)
);

CREATE TABLE marketplace.review (
  id uuid NOT NULL,
  member_id uuid NOT NULL REFERENCES marketplace.account(id),
  product_id uuid NOT NULL REFERENCES marketplace.product(id),
  rating integer NOT NULL,
  body text,
  visible boolean NOT NULL,
  removed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY (id),
  CHECK (rating BETWEEN 1 AND 5),
  CHECK (body IS NULL OR length(body) <= 2000)
);

CREATE TABLE marketplace.in_app_notification (
  id uuid NOT NULL,
  recipient_id uuid NOT NULL REFERENCES marketplace.account(id),
  kind text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at timestamptz,
  PRIMARY KEY (id),
  CHECK (jsonb_typeof(payload)='object')
);

CREATE TABLE marketplace.audit_entry (
  id uuid NOT NULL,
  actor_id uuid NOT NULL REFERENCES marketplace.account(id),
  action text NOT NULL,
  resource_type text NOT NULL,
  resource_id uuid NOT NULL,
  reason text,
  correlation_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE INDEX product_search_idx ON marketplace.product USING gin(search_document);
CREATE INDEX product_category_idx ON marketplace.product(category_id, published, id);
CREATE INDEX product_seller_idx ON marketplace.product(seller_id);
CREATE INDEX variant_product_idx ON marketplace.variant(product_id);
CREATE INDEX image_product_idx ON marketplace.product_image(product_id, sort_order);
CREATE INDEX address_member_idx ON marketplace.address(member_id);
CREATE INDEX cart_merge_idx ON marketplace.cart(merged_into_cart_id);
CREATE INDEX cart_line_variant_idx ON marketplace.cart_line(variant_id);
CREATE INDEX order_member_cursor_idx ON marketplace.customer_order(member_id, created_at DESC, id DESC);
CREATE INDEX portion_seller_idx ON marketplace.seller_order(seller_id, payment_status, customer_order_id);
CREATE INDEX line_portion_idx ON marketplace.order_line(seller_order_id);
CREATE INDEX line_product_idx ON marketplace.order_line(product_id, seller_order_id);
CREATE INDEX line_variant_idx ON marketplace.order_line(variant_id);
CREATE INDEX redemption_voucher_idx ON marketplace.voucher_redemption(voucher_id);
CREATE UNIQUE INDEX review_active_member_product_uq ON marketplace.review(member_id, product_id) WHERE removed_at IS NULL;
CREATE INDEX review_visible_idx ON marketplace.review(product_id, visible) WHERE removed_at IS NULL;
CREATE INDEX notification_owner_idx ON marketplace.in_app_notification(recipient_id, created_at DESC, id DESC);
CREATE INDEX audit_time_idx ON marketplace.audit_entry(created_at DESC, id DESC);
CREATE INDEX audit_resource_idx ON marketplace.audit_entry(resource_type, resource_id);
CREATE INDEX audit_actor_idx ON marketplace.audit_entry(actor_id);

CREATE FUNCTION marketplace.reject_audit_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit_entry is append-only';
END;
$$;

CREATE TRIGGER audit_append_only BEFORE UPDATE OR DELETE OR TRUNCATE
ON marketplace.audit_entry FOR EACH STATEMENT
EXECUTE FUNCTION marketplace.reject_audit_mutation();
