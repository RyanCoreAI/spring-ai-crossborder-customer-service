# WeChat inbound pagination

The five-page limit bounds a single worker turn, not the complete synchronization.
When the fifth batch still has `hasMore`, the service persists its cursor, returns
the webhook event to `RECEIVED`, and leaves the outbox `PENDING`. The existing due
event scheduler resumes it from the saved cursor. This continuation does not
consume a failure attempt. Only a batch with `hasMore=false` completes the event
and publishes the outbox.

A continuation with a blank or unchanged cursor follows the bounded failure
retry path instead of looping indefinitely or reporting success. Existing
message deduplication remains responsible for replayed provider messages.

`WechatWebhookPaginationTest` checks continuation across seven batches, state and
outbox transitions, and a stalled cursor using the real runtime service and local
adapter/storage doubles. No real WeChat credentials, callbacks, or sends are used.
The change does not add a distributed worker lease or prove crash recovery of an
already `PROCESSING` event; that remains a separate reliability limitation.
