package com.iqge;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compact native Markdown renderer modeled after IQ Code's text-first transcript. */
public final class MarkdownRenderer {
    /** Immutable renderer palette: no process-global theme mutation while a transcript is rebuilding. */
    private static final class Palette {
        final int text,plain,muted,codeBg,codeBgEnd,codeMeta,codeBorder,accent;
        final int keyword,string,number,comment,type,add,delete,addBg,deleteBg,inlineCode,inlineBg;
        Palette(int text,int plain,int muted,int codeBg,int codeBgEnd,int codeMeta,int codeBorder,int accent,
                int keyword,int string,int number,int comment,int type,int add,int delete,int addBg,int deleteBg,
                int inlineCode,int inlineBg){
            this.text=text;this.plain=plain;this.muted=muted;this.codeBg=codeBg;this.codeBgEnd=codeBgEnd;
            this.codeMeta=codeMeta;this.codeBorder=codeBorder;this.accent=accent;this.keyword=keyword;
            this.string=string;this.number=number;this.comment=comment;this.type=type;this.add=add;
            this.delete=delete;this.addBg=addBg;this.deleteBg=deleteBg;this.inlineCode=inlineCode;this.inlineBg=inlineBg;
        }
    }
    private static final Palette CLASSIC=new Palette(
            Color.rgb(238,234,228),Color.rgb(205,202,197),Color.rgb(150,145,138),
            Color.rgb(25,24,22),Color.rgb(25,24,22),Color.rgb(150,145,138),Color.TRANSPARENT,Color.rgb(126,178,136),
            Color.rgb(202,158,242),Color.rgb(155,199,139),Color.rgb(225,174,105),Color.rgb(111,117,120),Color.rgb(117,180,228),
            Color.rgb(126,178,136),Color.rgb(214,120,111),Color.argb(28,126,178,136),Color.argb(28,214,120,111),
            Color.rgb(231,184,145),Color.argb(38,255,255,255));
    private static final Palette NEON=new Palette(
            Color.rgb(244,243,255),Color.rgb(216,219,238),Color.rgb(142,149,179),
            Color.rgb(5,11,25),Color.rgb(18,24,52),Color.rgb(125,134,171),Color.rgb(72,70,139),Color.rgb(142,78,255),
            Color.rgb(190,159,255),Color.rgb(112,214,179),Color.rgb(244,184,106),Color.rgb(105,115,143),Color.rgb(101,199,255),
            Color.rgb(28,190,145),Color.rgb(255,111,132),Color.argb(48,28,190,145),Color.argb(48,255,111,132),
            Color.rgb(220,204,255),Color.argb(60,125,96,255));
    private static final Palette DAY=new Palette(
            Color.rgb(40,39,35),Color.rgb(72,69,63),Color.rgb(112,106,97),
            Color.rgb(243,242,237),Color.rgb(243,242,237),Color.rgb(112,106,97),Color.rgb(218,214,205),Color.rgb(55,105,75),
            Color.rgb(109,40,217),Color.rgb(11,122,90),Color.rgb(180,83,9),Color.rgb(122,117,107),Color.rgb(11,116,141),
            Color.rgb(22,115,76),Color.rgb(180,35,46),Color.argb(34,22,115,76),Color.argb(34,180,35,46),
            Color.rgb(55,105,75),Color.argb(24,55,105,75));
    /**
     * Emphasis delimiters are searched only within this window. Without the bound a long reply
     * full of stray {@code *} or {@code _} characters turned the inline parse into a quadratic scan
     * on the UI thread; beyond the window the delimiter stays literal.
     */
    private static final int EMPHASIS_SCAN_WINDOW = 512;
    /** Code spans and link URLs are allowed to be longer than a single emphasis run. */
    private static final int TOKEN_SCAN_WINDOW = 4096;
    /** Above these sizes the parse is skipped so a single frame stays bounded (text stays plain). */
    private static final int MAX_INLINE_PARSE_CHARS = 20_000;
    private static final int MAX_HIGHLIGHT_CHARS = 40_000;
    /**
     * Hard ceiling on how much Markdown one message may turn into views. A single very long reply
     * otherwise becomes one enormous {@link TextView}; its layout alone allocates enough to OOM or
     * ANR the UI thread when a long session is reopened. The tail is replaced by a short notice.
     */
    private static final int MAX_RENDER_CHARS = 60_000;
    /** Rows past this are dropped from a table so one huge paste cannot build thousands of cards. */
    private static final int MAX_TABLE_ROWS = 60;
    /** Columns past this are dropped from a table: 50 columns of cards would stall the UI thread. */
    private static final int MAX_TABLE_COLS = 10;

    private static List<String> headCells(List<String> row,int cols){
        return row.size()<=cols?row:new ArrayList<>(row.subList(0,cols));
    }
    /** A fenced block longer than this is clipped before it reaches the scrolling code view. */
    private static final int MAX_CODE_CHARS = 20_000;

    private MarkdownRenderer(){}

    public static LinearLayout render(Context c,String markdown,float textSp){
        return render(c,markdown,textSp,false,false);
    }

    public static LinearLayout render(Context c,String markdown,float textSp,boolean neonTheme){
        return render(c,markdown,textSp,neonTheme,false);
    }

    public static LinearLayout render(Context c,String markdown,float textSp,boolean neonTheme,boolean lightTheme){
        Palette palette=lightTheme?DAY:neonTheme?NEON:CLASSIC;
        LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);
        if(markdown==null)markdown="";
        if(markdown.length()>MAX_RENDER_CHARS)markdown=capMarkdown(markdown);
        String[] lines=markdown.replace("\r\n","\n").split("\n",-1);
        StringBuilder prose=new StringBuilder();
        for(int i=0;i<lines.length;i++){
            String line=lines[i];
            if(line.startsWith("```")){
                flushProse(c,root,prose,textSp,palette);
                String lang=line.substring(3).trim();StringBuilder code=new StringBuilder();i++;
                while(i<lines.length&&!lines[i].startsWith("```")){if(code.length()>0)code.append('\n');code.append(lines[i]);i++;}
                root.addView(codeBlock(c,lang,code.toString(),palette,neonTheme),new LinearLayout.LayoutParams(-1,-2));
            }else if(line.indexOf('|')>=0&&i+1<lines.length&&isTableSeparator(lines[i+1])){
                flushProse(c,root,prose,textSp,palette);
                List<String> header=tableCells(line);List<List<String>> rows=new ArrayList<>();int j=i+2;
                while(j<lines.length&&lines[j].indexOf('|')>=0){rows.add(tableCells(lines[j]));j++;}
                if(rows.isEmpty()){
                    // Header with no body: the reply was cut mid-table, so keep the line readable as text.
                    if(prose.length()>0)prose.append('\n');prose.append(line);i=i+1;
                }else{
                    int rowOmit=Math.max(0,rows.size()-MAX_TABLE_ROWS);
                    if(rowOmit>0)rows=new ArrayList<>(rows.subList(0,MAX_TABLE_ROWS));
                    int colsAll=header.size();for(List<String> row:rows)colsAll=Math.max(colsAll,row.size());
                    int colOmit=Math.max(0,colsAll-MAX_TABLE_COLS);
                    if(colOmit>0){header=headCells(header,MAX_TABLE_COLS);for(int k=0;k<rows.size();k++)rows.set(k,headCells(rows.get(k),MAX_TABLE_COLS));}
                    root.addView(tableBlock(c,header,rows,textSp,palette,rowOmit,colOmit),new LinearLayout.LayoutParams(-1,-2));
                    i=j-1;
                }
            }else{if(prose.length()>0)prose.append('\n');prose.append(line);}
        }
        flushProse(c,root,prose,textSp,palette);return root;
    }

    /**
     * Clips an oversized reply to {@link #MAX_RENDER_CHARS}. An odd number of fences is closed so
     * the truncated tail does not render as one giant code block, and the notice is plain prose.
     */
    private static String capMarkdown(String markdown){
        String head=markdown.substring(0,MAX_RENDER_CHARS);
        int fences=0;for(String line:head.split("\n",-1))if(line.startsWith("```"))fences++;
        StringBuilder capped=new StringBuilder(head);
        if(fences%2!=0)capped.append("\n```");
        capped.append("\n\n*… 内容过长，仅渲染前 ").append(MAX_RENDER_CHARS)
              .append(" 个字符；完整内容可在消息菜单中复制。*");
        return capped.toString();
    }

    private static void flushProse(Context c,LinearLayout root,StringBuilder prose,float textSp,Palette palette){
        if(prose.length()==0)return;String raw=prose.toString();prose.setLength(0);
        for(String chunk:raw.split("\\n\\s*\\n")){
            if(chunk.trim().isEmpty())continue;
            renderChunk(c,root,chunk,textSp,palette);
        }
    }

    /** 一个空行分隔的段落块：逐行分类为标题/列表/引用/分隔线/普通段落，各按层级样式上屏。 */
    private static void renderChunk(Context c,LinearLayout root,String chunk,float textSp,Palette palette){
        String[] lines=chunk.split("\n",-1);
        StringBuilder para=new StringBuilder();
        LinearLayout listHost=null;
        for(int i=0;i<lines.length;i++){
            String line=lines[i];
            if(isHr(line)){
                flushPara(c,root,para,textSp,palette);listHost=null;
                root.addView(hrView(c,palette),new LinearLayout.LayoutParams(-1,-2));
                continue;
            }
            int level=headingLevel(line);
            if(level>0){
                flushPara(c,root,para,textSp,palette);listHost=null;
                root.addView(headingView(c,level,line.substring(level+1).trim(),textSp,palette),new LinearLayout.LayoutParams(-1,-2));
                continue;
            }
            if(line.startsWith(">")&&(line.length()==1||line.charAt(1)==' ')){
                flushPara(c,root,para,textSp,palette);listHost=null;
                LinearLayout box=vbox(c);box.setPadding(0,0,0,dp(c,8));
                box.addView(quoteRow(c,line.length()==1?"":line.substring(2),textSp,palette),new LinearLayout.LayoutParams(-1,-2));
                root.addView(box,new LinearLayout.LayoutParams(-1,-2));
                continue;
            }
            int[] list=listMarker(line);
            if(list!=null){
                flushPara(c,root,para,textSp,palette);
                if(listHost==null){
                    listHost=new LinearLayout(c);listHost.setOrientation(LinearLayout.VERTICAL);listHost.setPadding(0,0,0,dp(c,8));
                    root.addView(listHost,new LinearLayout.LayoutParams(-1,-2));
                }
                String marker=list[1]==0?"•":list[1]==2?"☐":list[1]==3?"☑":line.substring(list[3],list[4]);
                listHost.addView(listRow(c,list[0],list[1],marker,line.substring(list[4]),textSp,palette),new LinearLayout.LayoutParams(-1,-2));
                continue;
            }
            listHost=null;
            if(para.length()>0)para.append('\n');
            para.append(line);
        }
        flushPara(c,root,para,textSp,palette);
    }

    private static void flushPara(Context c,LinearLayout root,StringBuilder para,float textSp,Palette palette){
        if(para.length()==0)return;
        String text=para.toString();para.setLength(0);
        TextView t=new TextView(c);t.setTextSize(textSp);t.setTextColor(palette.text);t.setTextIsSelectable(true);
        t.setLineSpacing(dp(c,2),1.18f);t.setPadding(0,dp(c,1),0,dp(c,9));t.setText(inline(text,palette));t.setLinksClickable(true);t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        root.addView(t,new LinearLayout.LayoutParams(-1,-2));
    }

    /** 返回 {嵌套层级, 标记类型(0 圆点 1 序号 2 未勾选 3 已勾选), 标记起, 标记止, 内容起}；非列表行返回 null。 */
    private static int[] listMarker(String line){
        int sp=0;while(sp<line.length()&&line.charAt(sp)==' ')sp++;
        if(sp>=line.length())return null;
        int p=sp;char ch=line.charAt(p);
        if((ch=='-'||ch=='*'||ch=='+')&&p+1<line.length()&&line.charAt(p+1)==' '){
            int cs=p+2;
            if(line.length()-cs>=3&&line.charAt(cs)=='['&&line.charAt(cs+2)==']'){
                char mark=line.charAt(cs+1);
                if(mark==' ')return new int[]{sp/2,2,cs,cs+1,cs+3};
                if(mark=='x'||mark=='X')return new int[]{sp/2,3,cs,cs+1,cs+3};
            }
            return new int[]{sp/2,0,p,p+1,cs};
        }
        int q=p;while(q<line.length()&&line.charAt(q)>='0'&&line.charAt(q)<='9')q++;
        if(q>p&&q+1<line.length()&&line.charAt(q)=='.'&&line.charAt(q+1)==' '){
            return new int[]{sp/2,1,p,q+1,q+2};
        }
        return null;
    }

    private static View listRow(Context c,int level,int kind,String marker,String body,float textSp,Palette palette){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.TOP);
        if(level>0)row.setPadding(dp(c,14*level),0,0,0);
        TextView m=new TextView(c);m.setText(marker);m.setTextSize(textSp);
        m.setTextColor(kind==0||kind==1?palette.accent:kind==2?palette.muted:palette.add);
        m.setPadding(0,0,dp(c,7),0);m.setMinWidth(dp(c,kind==1?16:9));
        row.addView(m,new LinearLayout.LayoutParams(-2,-2));
        TextView t=new TextView(c);t.setText(inline(body,palette));t.setTextIsSelectable(true);
        t.setTextSize(textSp);t.setTextColor(palette.text);t.setLineSpacing(dp(c,2),1.18f);
        t.setPadding(0,0,0,dp(c,3));
        row.addView(t,new LinearLayout.LayoutParams(-1,-2));
        return row;
    }

    private static View quoteRow(Context c,String body,float textSp,Palette palette){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        View bar=new View(c);bar.setBackgroundColor(palette.accent);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(c,3),-1);
        row.addView(bar,bp);
        TextView t=new TextView(c);t.setText(inline(body,palette));t.setTextIsSelectable(true);
        t.setTextSize(textSp);t.setTextColor(palette.muted);t.setLineSpacing(dp(c,2),1.15f);
        t.setPadding(dp(c,10),dp(c,2),0,dp(c,2));
        row.addView(t,new LinearLayout.LayoutParams(-1,-2));
        return row;
    }

    private static View headingView(Context c,int level,String body,float textSp,Palette palette){
        TextView t=new TextView(c);t.setText(inline(body,palette));t.setTextIsSelectable(true);
        t.setTypeface(Typeface.DEFAULT_BOLD);t.setTextColor(palette.text);
        t.setTextSize(textSp+(level==1?4f:level==2?2.5f:level==3?1.5f:0.5f));
        t.setPadding(0,dp(c,level<=2?14:8),0,dp(c,8));
        return t;
    }

    private static View hrView(Context c,Palette palette){
        LinearLayout box=vbox(c);box.setPadding(0,dp(c,6),0,dp(c,12));
        View line=new View(c);line.setBackgroundColor(Color.argb(40,Color.red(palette.text),Color.green(palette.text),Color.blue(palette.text)));
        box.addView(line,new LinearLayout.LayoutParams(-1,Math.max(1,dp(c,1))));
        return box;
    }

    private static boolean isHr(String line){
        String t=line.trim();
        if(t.length()<3)return false;
        char ch=t.charAt(0);
        if(ch!='-'&&ch!='*'&&ch!='_')return false;
        for(int i=1;i<t.length();i++)if(t.charAt(i)!=ch)return false;
        return true;
    }

    private static int headingLevel(String line){
        int i=0;while(i<line.length()&&line.charAt(i)=='#')i++;
        if(i==0||i>6)return 0;
        if(i<line.length()&&line.charAt(i)==' ')return i;
        return 0;
    }

    /**
     * Parse the inline Markdown used by model replies into spans while removing
     * the Markdown delimiter characters themselves. The old renderer only put
     * a bold span over "**text**", which is why the two asterisks remained
     * visible in chat.
     *
     * This parser intentionally stays small and Android-native, but covers the
     * syntax IQ Code emits most often: code, bold, italic, strike-through and
     * links. Nested emphasis is supported recursively.
     */
    public static CharSequence inlineText(String src){ return inline(src,CLASSIC); }
    public static CharSequence inlineText(String src,boolean neonTheme){ return inlineText(src,neonTheme,false); }
    public static CharSequence inlineText(String src,boolean neonTheme,boolean lightTheme){ return inline(src,lightTheme?DAY:neonTheme?NEON:CLASSIC); }

    private static CharSequence inline(String src,Palette palette){
        if(src==null)return new SpannableStringBuilder();
        if(src.length()>MAX_INLINE_PARSE_CHARS)return new SpannableString(src);
        SpannableStringBuilder out=new SpannableStringBuilder();
        appendInline(out,src,0,src.length(),palette,0);
        return out;
    }

    private static void appendInline(SpannableStringBuilder out,String src,int from,int to,Palette palette,int depth){
        if(depth>=24){out.append(src,from,to);return;}
        int i=from;
        while(i<to){
            // Backslash escaping: \* \_ \` \~ \[ and \\.
            if(src.charAt(i)=='\\'&&i+1<to&&"*_`~[\\".indexOf(src.charAt(i+1))>=0){
                out.append(src.charAt(i+1));i+=2;continue;
            }

            // Inline code has the highest priority. Markdown inside code is
            // deliberately not parsed.
            if(src.charAt(i)=='`'){
                int close=scanForward(src,'`',i+1,to);
                if(close>i+1&&close<to){
                    int st=out.length();out.append(src,i+1,close);int en=out.length();
                    out.setSpan(new ForegroundColorSpan(palette.inlineCode),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new TypefaceSpan("monospace"),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(palette.inlineBg),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i=close+1;continue;
                }
            }

            // Links: [label](https://...). Keep only the label on screen.
            if(src.charAt(i)=='['){
                int mid=scanForward(src,"](",i+1,to);
                if(mid>i+1&&mid<to){
                    int close=scanForward(src,')',mid+2,to);
                    if(close>mid+2&&close<to){
                        String url=src.substring(mid+2,close).trim();
                        int st=out.length();appendInline(out,src,i+1,mid,palette,depth+1);int en=out.length();
                        if(en>st){
                            out.setSpan(new ForegroundColorSpan(palette.type),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            out.setSpan(new URLSpan(url),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        i=close+1;continue;
                    }
                }
            }

            // Bold: **text** and __text__. Remove both delimiter pairs.
            if(i+1<to&&((src.charAt(i)=='*'&&src.charAt(i+1)=='*')||(src.charAt(i)=='_'&&src.charAt(i+1)=='_'))){
                String token=src.substring(i,i+2);int close=scanForward(src,token,i+2,to);
                if(close>i+2&&close<to){
                    int st=out.length();appendInline(out,src,i+2,close,palette,depth+1);int en=out.length();
                    if(en>st)out.setSpan(new StyleSpan(Typeface.BOLD),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i=close+2;continue;
                }
            }

            // Strike-through: ~~text~~.
            if(i+1<to&&src.charAt(i)=='~'&&src.charAt(i+1)=='~'){
                int close=scanForward(src,"~~",i+2,to);
                if(close>i+2&&close<to){
                    int st=out.length();appendInline(out,src,i+2,close,palette,depth+1);int en=out.length();
                    if(en>st)out.setSpan(new StrikethroughSpan(),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i=close+2;continue;
                }
            }

            // Italic: *text* and _text_. Don't treat ** / __ as italic.
            char ch=src.charAt(i);
            if((ch=='*'||ch=='_')&&(i+1>=to||src.charAt(i+1)!=ch)){
                int close=findSingleDelimiter(src,ch,i+1,Math.min(to,i+1+EMPHASIS_SCAN_WINDOW));
                if(close>i+1){
                    int st=out.length();appendInline(out,src,i+1,close,palette,depth+1);int en=out.length();
                    if(en>st)out.setSpan(new StyleSpan(Typeface.ITALIC),st,en,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i=close+1;continue;
                }
            }

            out.append(src.charAt(i));i++;
        }
    }

    /** Literal scan for a closing token that stops at {@link #TOKEN_SCAN_WINDOW} past {@code from}. */
    private static int scanForward(String src,char token,int from,int to){
        int limit=Math.min(to,from+TOKEN_SCAN_WINDOW);
        for(int i=Math.max(0,from);i<limit;i++)if(src.charAt(i)==token)return i;
        return -1;
    }

    private static int scanForward(String src,String token,int from,int to){
        int limit=Math.min(to,from+TOKEN_SCAN_WINDOW);
        for(int i=Math.max(0,from);i+token.length()<=limit;i++)if(src.startsWith(token,i))return i;
        return -1;
    }

    private static int findSingleDelimiter(String src,char delimiter,int from,int to){
        for(int i=from;i<to;i++){
            if(src.charAt(i)!=delimiter)continue;
            if(i>0&&src.charAt(i-1)=='\\')continue;
            if(i+1<to&&src.charAt(i+1)==delimiter)continue;
            return i;
        }
        return -1;
    }

    private static View codeBlock(Context c,String lang,String code,Palette palette,boolean neonTheme){
        String display=lang==null||lang.trim().isEmpty()?"text":lang.trim().toLowerCase(Locale.US);
        final String codeText = code!=null&&code.length()>MAX_CODE_CHARS ? code.substring(0,MAX_CODE_CHARS)+"\n… (代码过长，已截断)" : code;
        LinearLayout block=new LinearLayout(c);block.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=neonTheme?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{palette.codeBg,palette.codeBgEnd}):new GradientDrawable();
        if(!neonTheme)bg.setColor(palette.codeBg);bg.setCornerRadius(dp(c,12));if(neonTheme)bg.setStroke(dp(c,1),palette.codeBorder);block.setBackground(bg); block.setClipToOutline(true);

        LinearLayout meta=new LinearLayout(c);meta.setOrientation(LinearLayout.HORIZONTAL);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(dp(c,12),0,dp(c,4),0);
        TextView name=new TextView(c);name.setText(display);name.setTextSize(11f);name.setTextColor(palette.codeMeta);name.setTypeface(Typeface.MONOSPACE);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.addView(name,new LinearLayout.LayoutParams(0,dp(c,44),1));
        TextView copy=new TextView(c);copy.setText("复制");copy.setTextSize(11f);copy.setTextColor(palette.codeMeta);copy.setGravity(Gravity.CENTER);copy.setPadding(dp(c,12),0,dp(c,12),0);
        copy.setContentDescription("复制完整代码块");
        copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("code",code));copy.setText("已复制");UiMotion.contentUpdated(copy,true);copy.postDelayed(()->{copy.setText("复制");UiMotion.contentUpdated(copy,false);},900);});
        meta.addView(copy,new LinearLayout.LayoutParams(dp(c,68),dp(c,44)));block.addView(meta,new LinearLayout.LayoutParams(-1,dp(c,44)));

        HorizontalScrollView hs=new HorizontalScrollView(c);hs.setHorizontalScrollBarEnabled(false);hs.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        TextView body=new TextView(c);body.setText(highlight(codeText,display,palette));body.setTextColor(display.equals("text")?palette.plain:palette.text);body.setTextSize(12f);body.setTypeface(Typeface.MONOSPACE);body.setTextIsSelectable(true);body.setHorizontallyScrolling(true);body.setLineSpacing(dp(c,1),1.15f);body.setPadding(dp(c,12),0,dp(c,16),dp(c,14));
        hs.addView(body,new HorizontalScrollView.LayoutParams(-2,-2));block.addView(hs,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,4),0,dp(c,9));block.setLayoutParams(p);return block;
    }

    /** True where the transcript is wide enough for real columns: tablets and landscape phones. */
    private static boolean wideViewport(Context c){
        android.content.res.Configuration cfg=c.getResources().getConfiguration();
        return cfg.screenWidthDp>=720||(cfg.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&cfg.screenWidthDp>=560);
    }

    /** A GFM pipe table: the line under the header holds only dashes, colons, pipes and spaces. */
    private static boolean isTableSeparator(String line){
        String t=line.trim();if(t.indexOf('-')<0||t.indexOf('|')<0)return false;
        for(int i=0;i<t.length();i++){char ch=t.charAt(i);if(ch!='|'&&ch!='-'&&ch!=':'&&ch!=' ')return false;}
        return true;
    }

    private static List<String> tableCells(String line){
        String t=line.trim();if(t.startsWith("|"))t=t.substring(1);if(t.endsWith("|"))t=t.substring(0,t.length()-1);
        List<String> cells=new ArrayList<>();for(String cell:t.split("\\|",-1))cells.add(cell.trim());return cells;
    }

    private static String cellAt(List<String> row,int index){return row==null||index<0||index>=row.size()?"":row.get(index);}

    /** Rough column width in monospace units; CJK glyphs take two. */
    private static int cellUnits(String text){
        if(text==null)return 0;int units=0;
        for(int i=0;i<text.length();i++)units+=text.charAt(i)>0x2E80?2:1;
        return units;
    }

    /**
     * Tables are viewport aware. A tablet gets a grid with real columns inside a horizontal scroller; a
     * phone gets one card per row with the column name in front of every value, because a four column
     * table rendered as wrapped prose is unreadable on a 360dp screen.
     */
    private static View tableBlock(Context c,List<String> header,List<List<String>> rows,float textSp,Palette palette,int rowOmit,int colOmit){
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        if(wideViewport(c))gridTable(c,box,header,rows,textSp,palette);else cardTable(c,box,header,rows,textSp,palette);
        String note=rowOmit>0?"… 其余 "+rowOmit+" 行未显示":"";
        if(colOmit>0)note=(note.isEmpty()?"":" ")+"（另有 "+colOmit+" 列未显示）";
        if(!note.isEmpty()){
            TextView more=new TextView(c);more.setText(note);more.setTextSize(Math.max(10.5f,textSp-1.5f));more.setTextColor(palette.muted);more.setPadding(dp(c,4),dp(c,6),dp(c,4),0);
            box.addView(more,new LinearLayout.LayoutParams(-1,-2));
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,3),0,dp(c,9));box.setLayoutParams(p);return box;
    }

    private static void cardTable(Context c,LinearLayout box,List<String> header,List<List<String>> rows,float textSp,Palette palette){
        int cols=header.size();for(List<String> row:rows)cols=Math.max(cols,row.size());
        for(List<String> row:rows){
            LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(c,12),dp(c,9),dp(c,12),dp(c,11));
            GradientDrawable bg=new GradientDrawable();bg.setColor(palette.codeBg);bg.setCornerRadius(dp(c,12));bg.setStroke(dp(c,1),palette.codeBorder);card.setBackground(bg);
            String lead=cellAt(row,0);
            if(!lead.isEmpty()){
                TextView title=new TextView(c);title.setTextSize(textSp);title.setTextColor(palette.text);title.setTypeface(Typeface.DEFAULT_BOLD);title.setTextIsSelectable(true);
                title.setText(inline(lead,palette));card.addView(title,new LinearLayout.LayoutParams(-1,-2));
            }
            for(int j=1;j<cols;j++){
                if(j>=row.size()&&cellAt(row,j).isEmpty())continue;
                LinearLayout line=new LinearLayout(c);line.setOrientation(LinearLayout.HORIZONTAL);line.setPadding(0,dp(c,4),0,0);
                TextView label=new TextView(c);label.setTextSize(Math.max(10.5f,textSp-1.5f));label.setTextColor(palette.muted);
                label.setText(j<header.size()?header.get(j):("列 "+(j+1)));label.setPadding(0,dp(c,1),dp(c,8),dp(c,1));
                line.addView(label,new LinearLayout.LayoutParams(dp(c,86),-2));
                TextView value=new TextView(c);value.setTextSize(textSp-0.5f);value.setTextColor(palette.text);value.setTextIsSelectable(true);
                value.setText(inline(cellAt(row,j),palette));line.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                card.addView(line,new LinearLayout.LayoutParams(-1,-2));
            }
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(c,6));box.addView(card,cp);
        }
    }

    private static void gridTable(Context c,LinearLayout box,List<String> header,List<List<String>> rows,float textSp,Palette palette){
        int cols=header.size();for(List<String> row:rows)cols=Math.max(cols,row.size());
        int[] widths=new int[cols];
        for(int j=0;j<cols;j++){
            int units=cellUnits(cellAt(header,j));
            for(List<String> row:rows)units=Math.max(units,cellUnits(cellAt(row,j)));
            widths[j]=dp(c,Math.max(72,Math.min(240,units*7+22)));
        }
        LinearLayout grid=new LinearLayout(c);grid.setOrientation(LinearLayout.VERTICAL);
        grid.addView(gridRow(c,header,cols,widths,textSp,palette,true));
        for(List<String> row:rows)grid.addView(gridRow(c,row,cols,widths,textSp,palette,false));
        HorizontalScrollView hs=new HorizontalScrollView(c);hs.setHorizontalScrollBarEnabled(false);hs.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        hs.addView(grid,new HorizontalScrollView.LayoutParams(-2,-2));
        box.addView(hs,new LinearLayout.LayoutParams(-1,-2));
    }

    private static LinearLayout gridRow(Context c,List<String> row,int cols,int[] widths,float textSp,Palette palette,boolean head){
        LinearLayout line=new LinearLayout(c);line.setOrientation(LinearLayout.HORIZONTAL);
        for(int j=0;j<cols;j++){
            TextView cell=new TextView(c);
            cell.setTextSize(textSp-1);cell.setTextColor(head?palette.accent:palette.text);
            if(head)cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setGravity(Gravity.CENTER_VERTICAL);cell.setTextIsSelectable(true);cell.setMaxLines(4);
            cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.setPadding(dp(c,10),dp(c,7),dp(c,10),dp(c,7));
            GradientDrawable bg=new GradientDrawable();bg.setColor(head?palette.codeBgEnd:palette.codeBg);bg.setStroke(dp(c,1),palette.codeBorder);cell.setBackground(bg);
            cell.setText(inline(cellAt(row,j),palette));
            line.addView(cell,new LinearLayout.LayoutParams(widths[j],-2));
        }
        return line;
    }

    public static CharSequence highlight(String code,String lang){
        return highlight(code,lang,CLASSIC);
    }

    public static CharSequence highlight(String code,String lang,boolean neonTheme){
        return highlight(code,lang,neonTheme,false);
    }

    public static CharSequence highlight(String code,String lang,boolean neonTheme,boolean lightTheme){
        return highlight(code,lang,lightTheme?DAY:neonTheme?NEON:CLASSIC);
    }

    private static CharSequence highlight(String code,String lang,Palette palette){
        String raw=code==null?"":code;SpannableString s=new SpannableString(raw);String l=lang==null?"":lang.toLowerCase(Locale.US);
        if(raw.length()>MAX_HIGHLIGHT_CHARS)return s;
        if(l.equals("diff")||l.equals("patch")){
            int pos=0;for(String line:raw.split("\n",-1)){
                int end=Math.min(s.length(),pos+line.length());
                if(end>pos){
                    if(line.startsWith("+")&&!line.startsWith("+++")){s.setSpan(new ForegroundColorSpan(palette.add),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(new BackgroundColorSpan(palette.addBg),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
                    else if(line.startsWith("-")&&!line.startsWith("---")){s.setSpan(new ForegroundColorSpan(palette.delete),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(new BackgroundColorSpan(palette.deleteBg),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
                    else if(line.startsWith("@@"))s.setSpan(new ForegroundColorSpan(palette.accent),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                pos+=line.length()+1;
            }return s;
        }
        List<SpanSpec> specs=new ArrayList<>();
        if(l.contains("java")||l.contains("kotlin")||l.equals("kt")||l.contains("javascript")||l.equals("js")||l.contains("typescript")||l.equals("ts")){
            add(specs,raw,P_CODE_KEYWORDS,palette.keyword);
            add(specs,raw,P_CODE_TYPES,palette.type);
            add(specs,raw,P_NUMBER,palette.number);add(specs,raw,P_CODE_STRING,palette.string);add(specs,raw,P_CODE_COMMENT,palette.comment);
        }else if(l.equals("json")){add(specs,raw,P_JSON_KEY,palette.type);add(specs,raw,P_JSON_STRING,palette.string);add(specs,raw,P_JSON_LITERAL,palette.keyword);add(specs,raw,P_JSON_NUMBER,palette.number);
        }else if(l.equals("bash")||l.equals("sh")||l.equals("shell")||l.equals("zsh")){add(specs,raw,P_SHELL_KEYWORDS,palette.keyword);add(specs,raw,P_SHELL_STRING,palette.string);add(specs,raw,P_HASH_COMMENT,palette.comment);add(specs,raw,P_SHELL_VAR,palette.type);
        }else if(l.equals("python")||l.equals("py")){add(specs,raw,P_PY_KEYWORDS,palette.keyword);add(specs,raw,P_PY_STRING,palette.string);add(specs,raw,P_HASH_COMMENT,palette.comment);add(specs,raw,P_NUMBER,palette.number);
        }else if(l.equals("yaml")||l.equals("yml")){add(specs,raw,P_YAML_KEY,palette.type);add(specs,raw,P_HASH_COMMENT,palette.comment);add(specs,raw,P_JSON_LITERAL,palette.keyword);}
        else if(l.equals("markdown")||l.equals("md")){add(specs,raw,P_MD_HEADING,palette.type);add(specs,raw,P_MD_CODE,palette.string);add(specs,raw,P_MD_BOLD,palette.keyword);add(specs,raw,P_MD_LINK,palette.accent);add(specs,raw,P_MD_HR,palette.comment);add(specs,raw,P_MD_FRONTMATTER,palette.type);}
        for(SpanSpec x:specs)if(x.start>=0&&x.end<=s.length()&&x.end>x.start)s.setSpan(new ForegroundColorSpan(x.color),x.start,x.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;
    }
    private static void add(List<SpanSpec> out,String s,Pattern pattern,int color){try{Matcher m=pattern.matcher(s);while(m.find())out.add(new SpanSpec(m.start(),m.end(),color));}catch(Exception ignored){}}
    private static final class SpanSpec{final int start,end,color;SpanSpec(int s,int e,int c){start=s;end=e;color=c;}}
    private static LinearLayout vbox(Context c){
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;
    }

    private static int dp(Context c,int v){return(int)(v*c.getResources().getDisplayMetrics().density+0.5f);}

    // Syntax patterns are compiled once: highlight() runs for every rendered message, code block
    // and tool card, so recompiling per call was pure UI-thread overhead.
    private static final Pattern P_CODE_KEYWORDS = Pattern.compile("\\b(class|interface|enum|record|public|private|protected|static|final|void|int|long|double|float|boolean|new|return|if|else|for|while|switch|case|try|catch|finally|throw|throws|extends|implements|import|package|this|super|const|let|var|function|async|await|export|default|null|true|false)\\b");
    private static final Pattern P_CODE_TYPES = Pattern.compile("\\b(String|Object|Integer|Long|Boolean|List|Map|Set|File|JSONObject|JSONArray|Promise)\\b");
    private static final Pattern P_NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern P_CODE_STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern P_CODE_COMMENT = Pattern.compile("//.*$|/\\*[\\s\\S]*?\\*/", Pattern.MULTILINE);
    private static final Pattern P_JSON_KEY = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)");
    private static final Pattern P_JSON_STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern P_JSON_LITERAL = Pattern.compile("\\b(true|false|null)\\b");
    private static final Pattern P_JSON_NUMBER = Pattern.compile("-?\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern P_SHELL_KEYWORDS = Pattern.compile("\\b(if|then|else|elif|fi|for|do|done|while|case|esac|function|in|export|local|readonly)\\b");
    private static final Pattern P_SHELL_STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'");
    private static final Pattern P_HASH_COMMENT = Pattern.compile("#.*$", Pattern.MULTILINE);
    private static final Pattern P_SHELL_VAR = Pattern.compile("\\$\\{?[A-Za-z_][A-Za-z0-9_]*\\}?");
    private static final Pattern P_PY_KEYWORDS = Pattern.compile("\\b(def|class|import|from|as|if|elif|else|for|while|try|except|finally|with|return|yield|async|await|lambda|True|False|None|and|or|not|in|is)\\b");
    private static final Pattern P_PY_STRING = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern P_YAML_KEY = Pattern.compile("^[ \\t-]*[A-Za-z0-9_.-]+(?=\\s*:)", Pattern.MULTILINE);
    private static final Pattern P_MD_HEADING = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
    private static final Pattern P_MD_CODE = Pattern.compile("`[^`]+`");
    private static final Pattern P_MD_BOLD = Pattern.compile("\\*\\*[^*]+\\*\\*|__[^_]+__");
    private static final Pattern P_MD_LINK = Pattern.compile("\\[[^\\]]+\\]\\([^\\)]+\\)");
    private static final Pattern P_MD_HR = Pattern.compile("^(---|\\.\\.\\.)$", Pattern.MULTILINE);
    private static final Pattern P_MD_FRONTMATTER = Pattern.compile("^[A-Za-z0-9_.-]+(?=\\s*:)", Pattern.MULTILINE);
}
