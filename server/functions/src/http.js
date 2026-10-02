// HTTP routes for the gateway contract (docs/protocol.md §2) plus two dashboard routes.
'use strict';

const express = require('express');
const { BadRequest } = require('./core');

/**
 * @param service   from core.createService
 * @param verifyResponder async (req) -> { uid, name } | null. Dashboard routes need a responder (FR-28).
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
  app.post('/v1/contacts', route((req) => service.receiveContacts(req.body || {})));
  app.post('/v1/safe', route((req) => service.receiveSafe(req.body || {})));
  app.post('/v1/messages', route((req) => service.receiveMessages(req.body || {})));
  app.get('/v1/sos/status', route((req) => service.statuses(req.query.ids)));
  app.get('/v1/broadcasts', route((req) => service.broadcasts(req.query.since)));

  // Dashboard routes: responders only.
  const responder = (fn) => route(async (req) => {
    const user = await verifyResponder(req);
    if (!user) throw Object.assign(new BadRequest('responder sign-in required'), { status: 403 });
    return fn(req, user);
  });
  app.post('/v1/admin/sos/:id/status', responder((req, user) =>
    service.setStatus(req.params.id, String(req.body.status || ''), req.body.by || user.name || '')));
  app.post('/v1/admin/broadcasts', responder((req, user) =>
    service.addBroadcast(req.body.from || user.name || '', req.body.text)));

  return app;
}

module.exports = { createApp };
