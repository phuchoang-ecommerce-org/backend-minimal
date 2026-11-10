package com.phucle.ecommerce.identity.adapter.in.web;

import java.util.List;

record CartMergeResponse(boolean merged, List<Object> rejectedLines) {
}
