'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'admin.js'),'utf8');
const helper=source.slice(source.indexOf('function operationsLocalTime('),source.indexOf('function operationsAnnouncementEditor('));
const model=vm.runInNewContext(helper+'\n({operationsISO,operationsLocalTime});',{Date,Number,Error});
test('admin announcement time input keeps UTC+8 independent of host timezone',()=>{
  assert.equal(model.operationsISO('2026-10-28T00:00'),'2026-10-27T16:00:00.000Z');
  assert.equal(model.operationsLocalTime('2026-10-27T16:00:00Z'),'2026-10-28T00:00');
});
test('admin announcement form rejects overflowing or incomplete dates',()=>{
  for(const value of ['','2026-02-30T10:00','2026-13-01T10:00','2026-10-28','2026-10-28T24:00'])assert.throws(()=>model.operationsISO(value));
});

const labelSource=source.slice(source.indexOf('function operationsCertificateImageLabel('),source.indexOf('async function operationsWrite('));
const imageLabel=vm.runInNewContext(labelSource+'\noperationsCertificateImageLabel;');
test('review and compatibility pending images never receive an active-material label',()=>{
  assert.equal(imageLabel({source:'REVIEW',submissionId:'pending-1'}),'待审核新材料');
  assert.equal(imageLabel({source:'PENDING',submissionId:'pending-1'}),'待审核新材料');
  assert.equal(imageLabel({submissionId:'pending-1'}),'待审核新材料');
  assert.equal(imageLabel({source:'ACTIVE'}),'当前有效材料');
  assert.equal(imageLabel({}),'材料来源未确认');
});
