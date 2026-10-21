// HTTP routes for the gateway contract (docs/protocol.md §2) plus the dashboard routes.
'use strict';

const express = require('express');
const { BadRequest } = require('./core');

/**
 * @param service   from core.createService
 * @param verifyResponder async (req) -> { uid, name, role } | null. Dashboard routes need a responder or admin (FR-28).
 */
function createApp(service, { verifyResponder }) {
  const app = express();
  app.use(express.json({ limit: '256kb' }));

  const route = (fn) => async (req, res) => {
    try {
      res.json(await fn(req));
    } catch (e) {
      if (e instanceof BadRequest) res.status(e.status || 400).json({ error: e.message });
      else {
        console.error(e);
        res.status(500).json({ error: 'server error' });
      }
    }
  };

  // Gateway routes: any Pukaar phone with internet. Anonymous by design (gateways are strangers' phones).
  app.post('/v1/sos', route((req) => service.receiveSos(req.body || {})));
  app.post('/v1/sos/sms', route((req) => service.receiveSmsReport(req.body || {})));
  app.post('/v1/contacts', route((req) => service.receiveContacts(req.body || {})));
  app.post('/v1/safe', route((req) => service.receiveSafe(req.body || {})));
  app.post('/v1/messages', route((req) => service.receiveMessages(req.body || {})));
  app.get('/v1/sos/status', route((req) => service.statuses(req.query.ids)));
  app.get('/v1/broadcasts', route((req) => service.broadcasts(req.query.since)));
  app.post('/v1/broadcasts/seen', route((req) => service.broadcastsSeen(req.body || {})));

  // Dashboard routes: responders and admins. `user` is the signed-in operator, recorded in history.
  const responder = (fn) => route(async (req) => {
    const user = await verifyResponder(req);
    if (!user) throw new BadRequest('responder sign-in required', 403);
    return fn(req, req.body || {}, user);
  });
  app.post('/v1/admin/sos/:id/status', responder((req, body, user) =>
    service.setStatus(req.params.id, String(body.status || ''), {
      // `by` is the older name for who is going.
      assignee: body.assignee || body.by || '',
      note: body.note || '',
      actor: user,
    })));
  app.post('/v1/admin/sos/:id/notes', responder((req, body, user) => service.addNote(req.params.id, body.text, user)));
  app.post('/v1/admin/sos/:id/message', responder((req, body, user) => service.messagePerson(req.params.id, body.text, user)));
  app.post('/v1/admin/broadcasts', responder((req, body, user) =>
    service.addBroadcast(body.from || user.name || '', body.text, user)));

  return app;
}

module.exports = { createApp };
