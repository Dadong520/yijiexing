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

const pendingCountSource=source.slice(source.indexOf('var pendingCertsCount ='),source.indexOf('var pendingSupportCount ='));
function pendingCount(drivers){return vm.runInNewContext(pendingCountSource+'\npendingCertsCount;', {drivers,driversLoaded:drivers!==null});}
test('overview counts active replacement and legacy pending once per certificate and clears after review',()=>{
  const driver={certificates:[
    {type:'insurance',status:'APPROVED',pending:{submissionId:'new-1'}},
    {type:'license',status:'PENDING',pending:null},
    {type:'qualification',status:'PENDING',pending:{submissionId:'new-2'}}
  ]};
  assert.equal(pendingCount([driver]),3);
  driver.certificates.forEach(c=>{c.status='APPROVED';c.pending=null;});
  assert.equal(pendingCount([driver]),0);
  assert.equal(pendingCount([{},null,{certificates:[null,{status:'APPROVED',pending:null}]}]),0);
  assert.equal(pendingCount(null),'—');
});

const phase2Source=source.slice(source.indexOf('function nonNegInt('),source.indexOf('async function orderSettlementsAdmin('));
const phase2=vm.runInNewContext(phase2Source+'\n({nonNegInt,computeCouponPreview,validateAcceptSlots24h,validateScoreLevels});',{Number,String,Array,Error,Math});

test('coupon live preview computes exact cents issuance cost and enforces full-reduction threshold > amount',()=>{
  const ok=phase2.computeCouponPreview({
    name:'宜昌立减神券',type:'FULL_REDUCTION',amountYuan:'6.50',thresholdYuan:'20',
    totalQuantity:200,perUserLimit:2,validity:'DAYS',validDays:14,claimable:true
  });
  assert.equal(ok.valid,true);
  assert.equal(ok.amountCents,650);
  assert.equal(ok.thresholdCents,2000);
  assert.equal(ok.issuanceCostCents,130000);
  assert.equal(ok.label,'满20减6.50');

  const bad=phase2.computeCouponPreview({
    name:'门槛不足券',type:'FULL_REDUCTION',amountYuan:'15',thresholdYuan:'15',
    totalQuantity:50,perUserLimit:1,validity:'DAYS',validDays:7,claimable:false
  });
  assert.equal(bad.valid,false);
  assert.match(bad.error,/门槛须大于优惠面额/);

  const direct=phase2.computeCouponPreview({
    name:'新人无门槛券',type:'DIRECT',amountYuan:'5',thresholdYuan:'0',
    totalQuantity:100,perUserLimit:1,validity:'DAYS',validDays:30,claimable:false
  });
  assert.equal(direct.valid,true);
  assert.equal(direct.thresholdCents,0);
  assert.equal(direct.issuanceCostCents,50000);
  assert.equal(direct.label,'立减5元');
});

test('service score accept slots require exact 24h non-overlapping coverage when enabled',()=>{
  assert.equal(phase2.validateAcceptSlots24h([],false).ok,true);
  assert.equal(phase2.validateAcceptSlots24h([
    {start:'00:00',end:'07:00',points:2},
    {start:'07:00',end:'21:00',points:1},
    {start:'21:00',end:'00:00',points:2}
  ],true).ok,true);
  assert.equal(phase2.validateAcceptSlots24h([
    {start:'07:00',end:'21:00',points:1},
    {start:'21:00',end:'06:00',points:2}
  ],true).ok,false);
  assert.equal(phase2.validateAcceptSlots24h([
    {start:'00:00',end:'13:00',points:1},
    {start:'12:00',end:'00:00',points:1}
  ],true).ok,false);
});

test('service score 5-level validator enforces contiguous thresholds and open extremes',()=>{
  assert.equal(phase2.validateScoreLevels([
    {name:'卓越司机',min:110,max:null},
    {name:'优秀司机',min:100,max:110},
    {name:'标准司机',min:90,max:100},
    {name:'待提升司机',min:80,max:90},
    {name:'风险司机',min:null,max:80}
  ]).ok,true);
  assert.equal(phase2.validateScoreLevels([
    {name:'卓越司机',min:110,max:null},
    {name:'优秀司机',min:100,max:109},
    {name:'标准司机',min:90,max:100},
    {name:'待提升司机',min:80,max:90},
    {name:'风险司机',min:null,max:80}
  ]).ok,false);
});

test('all sidebar nav pages are registered in pages, navPaths and render dispatch',()=>{
  const navInit=source.slice(0,source.indexOf("$('#nav').innerHTML="));
  const pagesDef=navInit.match(/const pages=(\[\[.*?\]\]);/)[1];
  const navGroupsDef=navInit.match(/const navGroups=(\[\[.*?\]\]);/)[1];
  const navPathsBlock=source.slice(source.indexOf('const navPaths='),source.indexOf('const navGroups='));
  const renderMap=source.match(/await \(\{([^}]+)\}\[page\]\)\(\)/)[1].split(',').map(s=>s.trim());
  const ctx=vm.runInNewContext(`const pages=${pagesDef};${navPathsBlock};const navGroups=${navGroupsDef};({pages,navPaths,navGroups});`);
  const pageIds=new Set(ctx.pages.map(p=>p[0]));
  const renderIds=new Set(renderMap);
  for(const [,keys] of ctx.navGroups){
    for(const k of keys){
      assert.ok(pageIds.has(k),`missing page definition for ${k}`);
      assert.ok(renderIds.has(k),`missing render handler for ${k}`);
      assert.ok(Boolean(ctx.navPaths[k]),`missing nav icon path for ${k}`);
    }
  }
  for(const req of ['orderSettlementsAdmin','tariffsAdmin','serviceFeesAdmin','companySplitAccountsAdmin','companySplitLedgerAdmin','invoiceReissuesAdmin','techInvoicesAdmin','serviceScoreRuleAdmin','serviceScoreStatsAdmin','serviceScoreEntriesAdmin','couponPoolAdmin','couponCreateAdmin','newUserCouponAdmin','referralsAdmin','pointsMallAdmin']){
    assert.ok(pageIds.has(req)&&renderIds.has(req),`required feedback3 page missing: ${req}`);
  }
});
