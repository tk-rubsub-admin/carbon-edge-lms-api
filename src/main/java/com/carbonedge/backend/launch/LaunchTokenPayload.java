package com.carbonedge.backend.launch;

public record LaunchTokenPayload(
        String iss,
        Long sub,
        Long muid,
        Long cid,
        String redir,
        String jti,
        Long iat,
        Long exp
) {
}
