package com.phucle.ecommerce.identity.adapter.in.web;

import com.phucle.ecommerce.identity.api.CompletedLogin;
import java.util.List;

record CartMergeResponse(boolean merged, List<CompletedLogin.RejectedLine> rejectedLines) {
}
