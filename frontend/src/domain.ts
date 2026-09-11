export type Game = 'LOL' | 'OW';
export type Player = { id: string; name: string; tag: string; tier: string; score: number; preferredRoles: string[]; game: Game; source?: "sample" | "manual" | "demo-api"; riotId?: string; checkedAt?: string };
export type Match = { id: string; date: string; game: Game; a: Player[]; b: Player[]; result?: { a: number; b: number; memo: string } };
const names = ['오늘도탑차이','달빛정글','미드의품격','원딜은못말려','우리팀지킴이','한타의정석','새벽소환사','바텀듀오','포로수집가','협곡산책','용앞에서만나','점멸아껴요'];
export const roles: Record<Game,string[]> = { LOL: ['탑','정글','미드','원딜','서포터'], OW: ['탱커','딜러','서포터'] };
export const seed: Player[] = (['LOL','OW'] as Game[]).flatMap(game => names.map((name,i) => ({id:`${game}-${i}`,name:game === 'LOL' ? name : ['방벽세워요','힐팩어디','석양산책','메르시주세요','거점수호자','돌격대장','정밀조준','치유의빛','시간여행','함께돌격','수면명중','화물밀어요'][i],tag:`${game === 'LOL' ? '#KR1' : '#1234'}`,tier: i%3===0?'Emerald IV':i%3===1?'Platinum II':'Gold I',score:[2000,1840,1510,1620,1430,1920,1740,1380,1680,1590,1810,1460][i],preferredRoles:[roles[game][i%roles[game].length]],game})));
// 샘플 티어 표기도 확정된 400점 구간 / 100점 디비전 공식에 맞춘다.
seed.forEach(player => {
 if (player.game === 'OW') { player.tier = '수동 점수'; return; }
 const tiers = ['Iron', 'Bronze', 'Silver', 'Gold', 'Platinum', 'Emerald', 'Diamond'];
 const divisions = ['IV', 'III', 'II', 'I'];
 player.tier = `${tiers[Math.floor(player.score / 400)]} ${divisions[Math.floor(player.score % 400 / 100)]} · ${player.score % 100} LP`;
});
export const sum = (players: Player[]) => players.reduce((n,p)=>n+p.score,0);
// UI 데모용: 10명 전체 조합 중 총점 차이 최소. 실제 정책은 서버에서 계산한다.
export function balance(players: Player[]): [Player[],Player[]] {
 if(players.length!==10) throw new Error('참가자는 정확히 10명이어야 합니다.');
 let best: Player[] = []; let gap=Infinity;
 for(let mask=0;mask<1024;mask++) { if(!(mask&1)) continue; const a=players.filter((_,i)=>mask&(1<<i)); if(a.length!==5) continue; const diff=Math.abs(sum(players)-2*sum(a)); if(diff<gap){gap=diff;best=a;} }
 return [best,players.filter(p=>!best.includes(p))];
}
// 복수 선호 포지션과 게임별 역할 수를 고려해 표준 구성 가능 여부만 검사한다.
export function positionWarning(team: Player[], game: Game): string | null {
 const slots = game === 'LOL' ? roles.LOL : ['탱커','딜러','딜러','서포터','서포터'];
 function fits(index: number, used: Set<string>): boolean {
  if (index === slots.length) return true;
  return team.some(p => { if(used.has(p.id)||!p.preferredRoles.includes(slots[index]))return false;const next=new Set(used);next.add(p.id);return fits(index+1,next); });
 }
 return fits(0,new Set()) ? null : '선호 포지션만으로 표준 구성 충족 불가 · 포지션을 협의하거나 선수를 교체하세요.';
}
