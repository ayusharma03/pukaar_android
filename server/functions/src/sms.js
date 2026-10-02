// SMS senders for core.js.
'use strict';

/** Twilio REST API over fetch (Node 18+), so no SDK dependency. */
function twilioSms({ accountSid, authToken, from }) {
  const url = `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Messages.json`;
  const auth = 'Basic ' + Buffer.from(`${accountSid}:${authToken}`).toString('base64');
  return {
    async send(to, text) {
      const res = await fetch(url, {
        method: 'POST',
        headers: { Authorization: auth, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ To: to, From: from, Body: text }),
      });
      if (!res.ok) console.warn('Twilio send failed', res.status, await res.text());
      return res.ok;
    },
  };
}

/** No Twilio configured: log instead and report not sent. */
function logSms() {
  return {
    async send(to, text) {
      console.log(`[sms not configured] to ${to}: ${text}`);
      return false;
    },
  };
}

/** Records messages (tests and local demo runs). */
function fakeSms({ succeed = true } = {}) {
  const sent = [];
  return {
    sent,
    async send(to, text) {
      sent.push({ to, text });
      return succeed;
    },
  };
}

module.exports = { twilioSms, logSms, fakeSms };
