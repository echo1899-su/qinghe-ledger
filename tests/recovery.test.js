const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const L=require('../qinghe/assets/core.js');
const source=fs.readFileSync(require.resolve('../qinghe/assets/app.js'),'utf8');
function functionText(name,next){return source.slice(source.indexOf('function '+name+'('),source.indexOf('function '+next+'('));}
function setup(previous=L.initial(),writeResult='ok'){
 const calls=[];let confirm;
 const context=vm.createContext({L,loadError:'unreadable',native:true,s:null,book:'all',
 Android:{readPreviousState:()=>JSON.stringify({ok:true,raw:JSON.stringify(previous)}),saveRecoveredState:raw=>{calls.push(['save',JSON.parse(raw)]);return writeResult;},saveState:()=>{throw Error('normal write must not run');},setReminder:()=>calls.push(['reminder'])},
 confirmModal:(title,body,fn)=>{confirm=fn;},toast:t=>calls.push(['toast',t]),close:()=>calls.push(['close']),render:()=>calls.push(['render'])});
 vm.runInContext(functionText('persist','commit')+functionText('recoverPrevious','download'),context);
 return {context,calls,confirm:()=>confirm?.()};
}
test('failed startup blocks ordinary writes',()=>{const t=setup();assert.throws(()=>vm.runInContext('persist(L.initial())',t.context),/不能覆盖/);assert.equal(t.calls.length,0);});
test('previous snapshot must validate before confirmation or write',()=>{const s=L.initial();s.schema=99;const t=setup(s);vm.runInContext('recoverPrevious()',t.context);t.confirm();assert.equal(t.calls.filter(c=>c[0]==='save').length,0);assert.match(t.calls[0][1],/不可恢复/);});
test('checking recovery and cancelling do not write',()=>{const t=setup();vm.runInContext('recoverPrevious()',t.context);assert.equal(t.calls.length,0);assert.equal(t.context.loadError,'unreadable');});
test('successful recovery only activates validated state after saving',()=>{const t=setup();vm.runInContext('recoverPrevious()',t.context);t.confirm();assert.equal(t.calls[0][0],'save');assert.equal(t.context.loadError,'');assert.equal(t.context.s.schema,1);assert.ok(t.calls.some(c=>c[0]==='render'));});
test('failed native write preserves recovery page and memory state',()=>{const t=setup(L.initial(),'disk full');vm.runInContext('recoverPrevious()',t.context);t.confirm();assert.equal(t.context.loadError,'unreadable');assert.equal(t.context.s,null);assert.ok(!t.calls.some(c=>c[0]==='render'));assert.match(t.calls.at(-1)[1],/disk full/);});
