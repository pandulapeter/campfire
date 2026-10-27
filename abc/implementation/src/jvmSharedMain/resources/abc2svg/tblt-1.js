// abc2svg - ABC to SVG translator
// @source: https://chiselapp.com/user/moinejf/repository/abc2svg
// Copyright (C) 2014-2026 Jean-François Moine - LGPL3+
// tblt.js - module to generate tablatures
"use strict"
if(typeof abc2svg=="undefined")
var abc2svg={}
abc2svg.tblt={do_pscom:function(of,parm){function get_pit(t){if(!t)
return
var i=(t[0]=='^'||t[0]=='_')?1:0,p="C D EF G A Bc d ef g a b".indexOf(t[i++])
while(t[i]==','||t[i]=="'")
p+=t[i++]==','?-12:12
return p+((t[0]=='^'||t[i]=='#')?1:(t[0]=='_'||t[i]=='b')?-1:0)}
var tblt,C=abc2svg.C,glovar=this.glovar(),p_v=this.get_curvoice(),a=parm.split(/\s+/,3)
if(a.shift()!="tablature")
return of(parm)
if(!glovar.tblt_a)
glovar.tblt_a={}
if(a.length==1){if(!p_v)
return
tblt=glovar.tblt_a[a[0]]
if(tblt)
p_v.tblt=tblt
return}
a=parm.match(/(#\d)?\s*(pitch=[_^]?[A-Ga-g][,']*[b#]?)?\s+([0-9.]+)\s*([0-9.]*)\s*([0-9.]*)\s*(\w+)\s*(\w+)\s*(\w*)/)
if(!a)
return this.syntax(1,this.errs.bad_val,"%%tablature")
tblt={}
if(a[1])
glovar.tblt_a[a[1]]=tblt
if(a[2]){tblt.i=a[2].slice(6)
tblt.p=get_pit(tblt.i)}
if(a[5]){tblt.w=a[3]
tblt.d=+a[4]
tblt.a=+a[5]}else if(a[4]){tblt.w="0"
tblt.a=+a[3]
tblt.d=+a[4]}else{tblt.w="0"
tblt.a=0
tblt.d=+a[3]}
tblt.h=a[6]
tblt.n=a[7]
tblt.b=a[8]
if(p_v)
p_v.tblt=tblt},draw_tblt:function(of){var fnt,v,p_v,tblt,mus=this,C=abc2svg.C,glovar=mus.glovar(),v_tb=mus.get_voice_tb(),img=mus.get_img(),y=-glovar.music_h+glovar.tblt_h
function dr_no_pit(p_v,y){var j,ly,n,s
for(s=p_v.sym;s;s=s.next){if(s.a_ly){n=s.a_ly.length
break}}
if(!n)
return
mus.do_begin_end("ps",null,img.wx+" "+img.lm+" "+y+" "+n+" "+tblt.h)
for(j=0;j<n;j++){for(s=p_v.sym;s;s=s.next){if(s.bar_type){if(!tblt.b)
continue
mus.do_begin_end("ps",null,'('+s.bar_type+')'
+(s.x+img.lm).toFixed(2)
+" "+y
+" "+j+" "+tblt.b)}else if(s.a_ly){ly=s.a_ly[j]
if(!ly)
continue
mus.do_begin_end("ps",null,'('
+ly.t.replace(/\(|\)/,"\\$1")
+')'
+(s.x+img.lm).toFixed(2)
+" "+y
+" "+j+" "+tblt.n)}}}}
function dr_pit(p_v,y){var nt,tie,pit,s=p_v.sym
y=-glovar.music_h
mus.do_begin_end("ps",null,"gsave "+img.lm.toFixed(2)+' '+y
+" T("+p_v.tblt.i+")"+p_v.tblt.h)
for(;s;s=s.next){if(s.type!=C.NOTE)
continue
if(tie){tie=s.ti1
continue}
nt=s.notes[0]
pit=nt.midi-60-tblt.p
mus.do_begin_end("ps",null,((pit/36)|0)+" "+(pit%36)
+" "+s.x.toFixed(2)+" "+tblt.n)
tie=s.ti1}
mus.do_begin_end("ps",null,"grestore")}
of()
fnt=mus.get_font('vocal')
mus.do_begin_end("ps",null,"/"+fnt.name.split(',').slice(-1)+" "+fnt.size+" selectfont")
v=v_tb.length
while(--v>=0){p_v=v_tb[v]
tblt=p_v.tblt
if(!tblt)
continue
y-=tblt.d+2
if(!tblt.i)
dr_no_pit(p_v,y)
else
dr_pit(p_v,y)
y-=tblt.a+2}},set_glue:function(of,w){var s,st,v,p_v,p_st,tblt,glovar=this.glovar(),v_tb=this.get_voice_tb(),st_tb=this.get_staff_tb(),sw=0,tw=0,h=0
st=st_tb.length-1
v=v_tb.length
while(--v>=0){p_v=v_tb[v]
tblt=p_v.tblt
if(tblt){h+=tblt.a+tblt.d+4
if(tblt.w>tw)
tw=tblt.w
if(!tblt.i)
p_v.have_ly=0}}
glovar.tblt_h=h
v=v_tb.length
while(--v>=0){p_v=v_tb[v]
if(p_v.st!=st)
continue
for(s=p_v.sym;s.time==p_v.sym.time;s=s.next)
if(s.clef_type)
break
if(s.clef_type){s.ymn-=h
break}}
for(s=this.get_tsfirst();s;s=s.ts_next){if(s.shrink)
sw+=s.shrink
if(s.type==abc2svg.C.NOTE){if(sw<tw){while(!s.shrink)
s=s.ts_prev
s.shrink+=tw-sw}
break}}
of(w)},set_hooks:function(abc){abc.do_pscom=abc2svg.tblt.do_pscom.bind(abc,abc.do_pscom)
abc.draw_all_deco=abc2svg.tblt.draw_tblt.bind(abc,abc.draw_all_deco)
abc.set_sym_glue=abc2svg.tblt.set_glue.bind(abc,abc.set_sym_glue)}}
if(!abc2svg.mhooks)
abc2svg.mhooks={}
abc2svg.mhooks.tblt=abc2svg.tblt.set_hooks
