// Usage: node scripts/migrate-reference.mjs /path/to/reedance-reference
// Build-time data migration only: no JavaScript is executed by the Android app.
import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import crypto from 'node:crypto';
const root=process.argv[2]; if(!root) throw Error('Pass reference checkout path');
const load=async f=>import(pathToFileURL(path.resolve(root,'src',f)));
const {starterCourses}=await load('starterData.js');
const {songs}=await load('data.js');
const {demoQuiz}=await load('quizData.js');
const {enrichDemoLesson}=await load('learningData.js');
const src=enrichDemoLesson(JSON.parse(fs.readFileSync(path.join(root,'public/content/midnight-avenue.json'))));
const audio=fs.readFileSync(path.join(root,'public/audio/midnight-avenue-demo.wav'));
const example=(x)=>({text:x.example,translationFa:x.exampleFa||'',translationEn:x.example});
const lemma=w=>w.toLowerCase().replace(/[.,!?]+$/g,'');
const lesson={schemaVersion:1,songId:'midnight-avenue',targetLanguage:'en',locale:'en-GB',title:'Midnight Avenue',artist:'Reedance Originals',level:'B1',
 media:{audio:{path:'audio.wav',mimeType:'audio/wav',durationMs:64000,sha256:crypto.createHash('sha256').update(audio).digest('hex')}},
 credits:{lyrics:'Original demo material',audio:'Generated instrumental demo',noticeFa:src.disclaimerFa,noticeEn:src.disclaimerEn},
 lyrics:src.lyrics.map((l,i)=>({id:`line-${i+1}`,startMs:l.at*1000,text:l.en,translationFa:l.fa,translationEn:l.en,tokens:l.tokens.map(t=>({surface:t.w,translationFa:t.fa,translationEn:t.w,...(src.vocabulary[lemma(t.w)]?{lexemeId:'en:'+lemma(t.w)}:{})}))})),
 vocabulary:Object.entries(src.vocabulary).map(([w,v])=>({lexemeId:'en:'+w,lemma:w,normalizedKey:w,translationFa:v.fa,translationEn:w,ipa:v.ipa,faReading:v.faReading,partFa:v.partFa,partEn:v.partEn,example:example(v)})),
 grammar:src.grammar.map(g=>{const {exampleFa,...rest}=g;return {...rest,example:example(g)};}),
 idioms:src.idioms.map(i=>({id:i.id,text:i.en,translationFa:i.fa,translationEn:i.meaningEn})),
 sentences:src.sentences.map(i=>({id:i.id,text:i.en,translationFa:i.fa,translationEn:i.en,contextFa:i.contextFa,contextEn:i.contextEn})), 
 vocabularyTables:src.vocabularyTables,
 questions:demoQuiz.questions.map(q=>{const r={...q,relatedLexemeIds:(q.wordIds||[]).map(w=>'en:'+w)}; delete r.wordIds;delete r.at;if(r.choices){r.presentationOrder=r.choices;delete r.choices;}if(r.pairs)r.pairs=r.pairs.map(p=>({id:p.id,text:p.text,fa:p.fa,en:p.en,lexemeId:'en:'+p.id}));return r;})};
const out='android/app/src/';
const write=(f,x)=>fs.writeFileSync(out+f,JSON.stringify(x,null,2)+'\n');
write('main/assets/starter.json',starterCourses);write('main/assets/catalog_preview.json',songs);write('debug/assets/demo_lesson.json',lesson);
fs.writeFileSync(out+'debug/assets/midnight-avenue-demo.wav',audio);
