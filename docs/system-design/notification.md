# Notification Setup — Sentinel IoT Platform

The platform supports multiple alert notification providers. Enable exactly one (or none) per deployment.

---

## Slack (Recommended)

```bash
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
SLACK_NOTIFY_ENABLED=true
```

Create an Incoming Webhook at [api.slack.com/messaging/webhooks](https://api.slack.com/messaging/webhooks).

---

## Generic Webhook (PagerDuty, Opsgenie, Teams, etc.)

```bash
NOTIFY_WEBHOOK_URL=https://your-endpoint/alert
NOTIFY_WEBHOOK_ENABLED=true
NOTIFY_WEBHOOK_SECRET=your-hmac-secret   # optional — signs payload with HMAC-SHA256
```

---

## LINE Notify (Deprecated)

> **Warning:** LINE Notify was shut down on **March 31, 2025**. Tokens no longer work. Migrate to Slack webhook or the generic webhook provider.

```bash
LINE_NOTIFY_TOKEN=your_token
LINE_NOTIFY_ENABLED=true
```
