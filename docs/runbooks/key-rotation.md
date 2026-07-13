# Secret and JWT Key Rotation

## Admin JWT

1. Generate a new 256-bit `JWT_SECRET`.
2. Move the current value to `JWT_PREVIOUS_SECRET` for the short compatibility window.
3. Restart all instances with identical issuer/audience configuration.
4. Revoke refresh tokens created before the rotation boundary when immediate logout is required.
5. Remove `JWT_PREVIOUS_SECRET` after the maximum access-token lifetime.

Widget and admin tokens use distinct issuer/audience claims. A widget token must never authenticate an admin API.

## Integration Credentials

- Rotate `INTEGRATION_ENCRYPTION_KEY` through a dedicated re-encryption job before removing the previous key; changing it directly makes stored channel/Shopify tokens unreadable.
- Rotate WeChat, Shopify, and future Douyin credentials per tenant and record the actor in audit logs.
- Never expose decrypted values through admin DTOs, traces, or logs.

## Validation

- Old revoked refresh token returns 401.
- Current access token behavior matches the configured compatibility window.
- New outbound channel operation succeeds with re-encrypted credentials.
- `scripts/scan-sensitive-logs.ps1` finds no raw secret material.
