const {test}=require('node:test');
const assert=require('node:assert/strict');
const {parseRoom,parsePlan,recommend}=require('../website/room-bridge.js');
test('valid handoff retains original room dimensions and escapes through DOM text',()=>{
 const r=parseRoom('?cozy_length=4.12&cozy_width=3.24&cozy_room=Living%20room');
 assert.equal(r.length,4.12);assert.equal(r.width,3.24);assert.equal(r.name,'Living room');
});
test('rejects absent, invalid and unreasonable sizes',()=>{
 for(const q of ['', '?cozy_length=&cozy_width=3','?cozy_length=-1&cozy_width=3','?cozy_length=Infinity&cozy_width=3','?cozy_length=101&cozy_width=3'])assert.equal(parseRoom(q),null);
});
test('chooses smallest purchased area, rotates and rounds upwards',()=>{
 assert.deepEqual(recommend(4,3,[4,5],true),{width:5,length:3.1,area:15.5});
 assert.deepEqual(recommend(4,3,[4],true),{width:4,length:4.1,area:16.4});
 assert.deepEqual(recommend(4,3,[4],false),{width:4,length:3,area:12});
 assert.equal(recommend(5.2,5.2,[4,5],true),null);
 assert.equal(recommend(NaN,3,[4],true),null);
});

test('retains concave outline area instead of enclosing rectangle area',()=>{
 const points=[{x:0,y:0},{x:2,y:0},{x:2,y:1},{x:1,y:1},{x:1,y:2},{x:0,y:2}];
 const q=new URLSearchParams({cozy_length:'2',cozy_width:'2',cozy_plan:JSON.stringify(points)});
 const room=parseRoom('?'+q);
 assert.equal(room.plan.area,3);
 assert.deepEqual(room.plan.points,points);
});
test('rejects crossed outlines, malformed coordinates and oversized plans',()=>{
 for(const p of ['invalid',[],[{x:0,y:0},{x:2,y:2},{x:0,y:2},{x:2,y:0}],[{x:0,y:0},{x:'2',y:0},{x:1,y:2}],Array(41).fill({x:0,y:0})])assert.equal(parsePlan(p),null);
});
