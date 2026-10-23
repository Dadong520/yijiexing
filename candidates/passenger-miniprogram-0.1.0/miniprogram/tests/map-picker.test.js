const test=require('node:test')
const assert=require('node:assert/strict')
const {createPicker,normalize}=require('../lib/map-picker')
test('native selection and cancellation are separate; invalid coordinates rejected',async()=>{
  const wx={chooseLocation(opts){opts.success({name:'宜昌东站',latitude:30.665,longitude:111.37})}}
  const picker=createPicker(wx,{mode:'native'},()=>{})
  assert.equal((await picker.pickLocation({purpose:'origin'})).coordinateSystem,'GCJ02')
  wx.chooseLocation=opts=>opts.fail({errMsg:'chooseLocation:fail cancel'})
  assert.equal(await picker.pickLocation({purpose:'destination'}),null)
  assert.throws(()=>normalize({name:'未知',latitude:NaN,longitude:111},'wechat'))
})
test('plugin clears stale point before open, consumes exactly once and clears cancellation',async()=>{
  let value={name:'旧点',latitude:1,longitude:1},url=''
  const plugin={setLocation(v){value=v},getLocation(){return value}}
  const picker=createPicker({navigateTo(o){url=o.url;o.success()}},{mode:'plugin',key:'public-key',referer:'宜捷行'},()=>plugin)
  assert.deepEqual(await picker.pickLocation({purpose:'origin'}),{pendingPlugin:true})
  assert.equal(value,null);assert.ok(url.startsWith('plugin://chooseLocation/index?'))
  value={name:'新点',latitude:30.66,longitude:111.36}
  assert.equal(picker.consumePickedLocation().purpose,'origin');assert.equal(value,null)
  assert.equal(picker.consumePickedLocation(),null)
  await picker.pickLocation({purpose:'destination'});assert.equal(picker.consumePickedLocation(),null)
})
test('plugin failure releases selection and missing key prevents navigation',async()=>{
  let called=0
  const picker=createPicker({navigateTo(){called++}},{mode:'plugin',key:''},()=>{})
  await assert.rejects(picker.pickLocation({purpose:'origin'}),/尚未配置/)
  await assert.rejects(picker.pickLocation({purpose:'origin'}),/尚未配置/)
  assert.equal(called,0)
})
