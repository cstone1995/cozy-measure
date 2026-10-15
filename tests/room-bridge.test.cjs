const {test}=require('node:test');
const assert=require('node:assert/strict');
const {parseRoom,recommend}=require('../website/room-bridge.js');
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
