# Notification Setup — Sentinel IoT Platform

The platform supports multiple alert notification providers. Multiple providers can be enabled simultaneously. All outbound notifications are deduplicated per device/sensor within a configurable cooldown window to prevent alert storms.

---

## Deduplication (default: enabled, 5-minute cooldown)

Repeated alerts for the same device + sensor + severity are suppressed within the cooldown window. Alerts are still persisted to the database — only the outbound notification is suppressed.

```bash
NOTIFY_DEDUP_ENABLED=true
NOTIFY_DEDUP_COOLDOWN_MINUTES=5
```

---

## LINE Messaging API

> **Replaces LINE Notify**, which was permanently shut down on March 31, 2025.

**Pricing (verify your region at [developers.line.biz/en/docs/messaging-api/pricing/](https://developers.line.biz/en/docs/messaging-api/pricing/)):**

| Plan | Monthly fee | Free messages | Over quota |
|------|-------------|---------------|-----------|
| Light | Free | 200 | Not sent (error returned) |
| Standard | ¥5,000 | 5,000 | Charged per message |
| Premium | ¥15,000 | 30,000 | Up to ¥3/message |

> **Warning:** Message count is per recipient, not per message. Sending to a group of 50 users costs 50 messages. With no deduplication, a single Kafka poll burst can exhaust the free tier in minutes.

**Setup:**

1. Create a LINE Official Account and enable Messaging API at [developers.line.biz](https://developers.line.biz)
2. Issue a Long-lived Channel Access Token
3. Find the target `userId` (starts with `U`) or `groupId` (starts with `C`):
   - Add the bot as a friend (or to the group), send a message, then inspect the webhook event

```bash
LINE_MESSAGING_CHANNEL_TOKEN=your_channel_access_token
LINE_MESSAGING_TO=Uxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
LINE_MESSAGING_ENABLED=true
```

---

## Telegram Bot API

Free with no monthly message quota. Rate limit: 30 messages/second.

**Setup:**

1. Create a bot via [@BotFather](https://t.me/BotFather) — copy the token
2. Add the bot to the target group (or start a direct chat)
3. Obtain `chat_id`:
   - Send any message to the bot, then call `https://api.telegram.org/bot{TOKEN}/getUpdates`
   - Read `result[0].message.chat.id` (group IDs are negative, e.g. `-1001234567890`)

```bash
TELEGRAM_BOT_TOKEN=1234567890:ABCDefgh...
TELEGRAM_CHAT_ID=-1001234567890
TELEGRAM_ENABLED=true
```

---

## Apprise (self-hosted)

Apprise is an open-source notification gateway supporting 130+ services (Telegram, Discord, Slack, email, SMS, LINE, and more) through a single REST endpoint.

**Setup:**

```bash
docker run -p 8000:8000 caronc/apprise
```

Then configure notification URLs in the Apprise web UI (`http://localhost:8000`).

```bash
APPRISE_URL=http://apprise:8000
APPRISE_TAG=                    # optional — omit to notify all configured services
APPRISE_ENABLED=true
```

See: [github.com/caronc/apprise](https://github.com/caronc/apprise)

---

## Slack

```bash
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
SLACK_NOTIFY_ENABLED=true
```

Create an Incoming Webhook at [api.slack.com/messaging/webhooks](https://api.slack.com/messaging/webhooks). Rate limit: 1 message/second per channel.

---

## Generic Webhook (PagerDuty, Opsgenie, Teams, etc.)

```bash
NOTIFY_WEBHOOK_URL=https://your-endpoint/alert
NOTIFY_WEBHOOK_ENABLED=true
NOTIFY_WEBHOOK_SECRET=your-hmac-secret   # optional — signs payload with HMAC-SHA256
```
