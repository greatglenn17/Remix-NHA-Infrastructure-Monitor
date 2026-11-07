// Run: node --test web/logic.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from './logic.js';

test('roles that see every project', () => {
  assert.equal(L.seesAll({ role: 'SUPER_ADMIN' }), true);
  assert.equal(L.seesAll({ role: 'SUPER_VIEWER' }), true);
  assert.equal(L.seesAll({ role: 'FIELD_ENGINEER', allProjects: true }), true);
  assert.equal(L.seesAll({ role: 'ENGINEER_ADMIN' }), false);
  assert.equal(L.seesAll({}), false);
});

test('record data string is unpacked, bad JSON gives an empty row', () => {
  const r = L.parseRecord({ uuid: 'u', type: 'project_payments', revision: 3, data: '{"name":"1st billing","grossAmount":100}' });
  assert.equal(r.name, '1st billing');
  assert.equal(r.revision, 3);
  assert.equal(L.parseRecord({ uuid: 'u', data: '{oops' }).uuid, 'u');
});

test('contract cost: scope sum, fallback, approved variation orders', () => {
  const scope = JSON.stringify([{ name: 'A', amount: 1000, weightPct: 50 }, { name: 'B', amount: 1000, weightPct: 50 }]);
  assert.equal(L.originalCost({ scopeOfWork: scope, contractCostOriginal: 5 }), 2000);
  assert.equal(L.originalCost({ scopeOfWork: 'Site development', contractCostOriginal: 5 }), 5);
  const docs = [
    { documentName: 'Variation Order No. 1 - PHP 500.00', status: 'Approved' },
    { documentName: 'Variation Order No. 2 (Deductive) - PHP 200', status: 'Approved' },
    { documentName: 'Variation Order No. 3 - PHP 9,999', status: 'Pending' },
  ];
  assert.equal(L.currentCost({ scopeOfWork: scope }, docs), 2300);
  assert.equal(L.currentCost({ scopeOfWork: scope }, []), 2000);
});

test('scope items: JSON list or plain text', () => {
  assert.deepEqual(L.scopeItems('Road works'), [{ name: 'Road works', amount: 0, weightPct: 100 }]);
  assert.equal(L.scopeItems('[{"name":"A","amount":1,"weightPct":100},{"name":" "}]').length, 1);
});

test('days left, schedule words, dates, money, attachment paths', () => {
  const p = { completionDateOriginal: '2026-11-10', completionDateRevised: '-', actualAccomplishment: 40, targetAccomplishment: 45.5 };
  assert.equal(L.daysLeftText(p, '2026-11-07'), '3 days');
  assert.equal(L.daysLeftText({ ...p, completionDateRevised: '2026-11-01' }, '2026-11-07'), 'Overdue by 6 days');
  assert.equal(L.daysLeftText({ ...p, actualAccomplishment: 100 }, '2026-11-07'), 'Completed');
  assert.equal(L.scheduleText(p), '5.50% behind');
  assert.equal(L.displayDate('2026-10-20'), 'Oct 20, 2026');
  assert.equal(L.displayDate('TBA'), 'TBA');
  assert.equal(L.formatPhp(-1234.5), '-₱1,234.50');
  assert.equal(L.netAmount({ grossAmount: 1000, retentionPct: 10 }), 900);
  assert.equal(L.isOpenIssue({ status: 'Pending' }), true);
  assert.equal(L.isOpenIssue({ status: 'Resolved' }), false);
  const id = '0123abcd-0123-0123-0123-0123456789ab';
  assert.equal(L.storagePathOf(`nhafile:${id}/${id}.jpg`), `attachments/${id}/${id}.jpg`);
  assert.equal(L.storagePathOf(`nhafile:${id}.pdf`), `attachments/${id}.pdf`);
  assert.equal(L.storagePathOf('https://example.com/x.jpg'), null);
});
