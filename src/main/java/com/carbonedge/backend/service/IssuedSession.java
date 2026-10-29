package com.carbonedge.backend.service;

import com.carbonedge.backend.model.AuthSession;

public record IssuedSession(AuthSession session, String refreshToken) {
}
