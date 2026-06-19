package com.caseaxis.auth;

import java.util.List;

public record LoginResponse(String username, List<String> roles) {}
