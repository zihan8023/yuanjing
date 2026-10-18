package com.iqge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.app.iqcode.model.PlanWorkflowState;
import com.termux.app.iqcode.tasks.TaskStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compact Claude Code-style live activity and task panel. */
public final class AgentProgressView extends LinearLayout {
    private static final long COMPLETED_HOLD_MS=5000L;
    private static final long STEPS_HOLD_MS=4000L;
    private static final long FADE_MS=500L;
    /** 划掉的条目只短暂停留，到点自动消失。 */
    private static final long TASK_DONE_TTL_MS=4000L;
    private static final long STEP_DONE_TTL_MS=2500L;

    /** 一条工作步骤：开始时 done=false，完成置 true（列表里划掉）。 */
    public static final class Step {
        public final String label;
        public boolean done;
        public Step(String label){this.label=label==null?"":label;}
    }

    private final Handler handler=new Handler(Looper.getMainLooper());
    private final TextView status;
    private final TextView reasoning;
    private final ArcProgressView arc;
    private final LinearLayout steps;
    private final LinearLayout tasks;
    private boolean animating;
    private long completedHoldUntil;
    private int textColor=0xffeee7d8,mutedColor=0xffa39d90,accentColor=0xff8fa58f,greenColor=0xff8fa58f,surfaceColor=0xff191816;
    private String fallbackStatus="正在处理…";
    private TaskStore.Snapshot snapshot;
    private PlanWorkflowState planState=PlanWorkflowState.idle();
    private long renderedTaskVersion=Long.MIN_VALUE;
    private long renderedPlanRevision=Long.MIN_VALUE;
    private boolean renderedBusy;
    private String renderedActivity="";
    private int outputBacklog;
    private final List<Step> stepsList=new ArrayList<>();
    private long stepsFadeAt;
    private final java.util.Map<String,Long> completedTaskAt=new java.util.HashMap<>();
    private final java.util.Map<String,Long> stepDoneAt=new java.util.HashMap<>();

    public AgentProgressView(Context context){
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL);
        // 与滚木条同一套几何：12/6 内边距、16dp 圆角，两条横条宽度一致、观感统一。
        setPadding(dp(12),dp(6),dp(12),dp(6));
        GradientDrawable surface=new GradientDrawable();surface.setColor(surfaceColor);surface.setCornerRadius(dp(16));setBackground(surface);
        // 任务行/步骤行增删时高度平滑过渡，不再瞬间跳变。
        UiMotion.enableLayoutChanges(this);
        arc=new ArcProgressView(context);LayoutParams arcLp=new LayoutParams(dp(24),dp(24));arcLp.setMargins(0,dp(1),dp(9),0);addView(arc,arcLp);
        LinearLayout content=new LinearLayout(context);content.setOrientation(VERTICAL);content.setGravity(Gravity.CENTER_VERTICAL);
        status=new TextView(context);status.setTextSize(12f);status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);status.setGravity(Gravity.CENTER_VERTICAL);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(status,new LayoutParams(-1,dp(21)));
        reasoning=new TextView(context);reasoning.setTextSize(10.5f);reasoning.setTextColor(mutedColor);reasoning.setTypeface(Typeface.DEFAULT);reasoning.setMaxLines(2);reasoning.setEllipsize(android.text.TextUtils.TruncateAt.END);reasoning.setVisibility(GONE);reasoning.setGravity(Gravity.START);reasoning.setPadding(0,0,dp(4),dp(2));
        content.addView(reasoning,new LayoutParams(-1,-2));
        steps=new LinearLayout(context);steps.setOrientation(VERTICAL);steps.setPadding(0,dp(2),0,0);content.addView(steps,new LayoutParams(-1,-2));
        tasks=new LinearLayout(context);tasks.setOrientation(VERTICAL);tasks.setPadding(0,dp(1),0,0);content.addView(tasks,new LayoutParams(-1,-2));
        addView(content,new LayoutParams(0,-2,1));setVisibility(GONE);
    }

    public void setPalette(int text,int muted,int accent,int green){setPalette(text,muted,accent,green,surfaceColor);}
    public void setPalette(int text,int muted,int accent,int green,int surface){textColor=text;mutedColor=muted;accentColor=accent;greenColor=green;surfaceColor=surface;GradientDrawable background=new GradientDrawable();background.setColor(surfaceColor);background.setCornerRadius(dp(16));setBackground(background);reasoning.setTextColor(muted);arc.setColor(accent);render();}

    public void setOutputBacklog(int codePoints){
        outputBacklog=Math.max(0,Math.min(240,codePoints));
        arc.setSpeed(outputBacklog==0?.55f:Math.min(1.8f,.55f+outputBacklog*.018f));
    }

    public void setReasoningPreview(String value){
        if(Looper.myLooper()!=Looper.getMainLooper()){post(()->setReasoningPreview(value));return;}
        reasoning.setText("");
        reasoning.setVisibility(GONE);
    }

    public void update(String activity,TaskStore.Snapshot next,PlanWorkflowState plan,boolean busy){
        String nextActivity=activity==null||activity.trim().isEmpty()?"正在处理…":activity.trim();
        boolean acceptedSnapshot=next!=null&&(snapshot==null||next.version>=snapshot.version);
        boolean newlyCompleted=acceptedSnapshot&&hasNewlyCompleted(snapshot,next);
        boolean completedVisibilityChanged=false;
        if(newlyCompleted){completedHoldUntil=System.currentTimeMillis()+COMPLETED_HOLD_MS;completedVisibilityChanged=true;}
        if(busy&&!newlyCompleted&&completedHoldUntil>0L){completedHoldUntil=0L;handler.removeCallbacks(tick);completedVisibilityChanged=true;}
        if(acceptedSnapshot){
            snapshot=next;
            // 记录每条任务的完成时刻：划掉的行到点自动消失；复活/删除的任务清掉计时。
            Set<String> stillCompleted=new HashSet<>();
            for(JSONObject t:snapshot.tasks)if("completed".equals(t.optString("status")))stillCompleted.add(t.optString("id"));
            completedTaskAt.keySet().retainAll(stillCompleted);
            long now=System.currentTimeMillis();
            for(String id:stillCompleted)completedTaskAt.putIfAbsent(id,now);
        }
        if(plan!=null&&(planState==null||plan.revision>=planState.revision))planState=plan.copy();
        boolean taskChanged=snapshot!=null&&snapshot.version!=renderedTaskVersion;
        boolean planChanged=planState!=null&&planState.revision!=renderedPlanRevision;
        boolean statusChanged=busy!=renderedBusy||!nextActivity.equals(renderedActivity)||planChanged;
        fallbackStatus=nextActivity;
        boolean hasOpenTasks=hasOpenTasks(snapshot);
        boolean holdingCompleted=completedHoldUntil>System.currentTimeMillis()&&hasCompletedTasks(snapshot);
        // 有待办任务就常驻显示（空闲也显示），否则只在执行中/等待确认/刚完成/步骤未完时出现。
        boolean visible=busy||holdingCompleted||hasOpenTasks||hasRunningSteps()||(planState!=null&&(planState.isPlanning()||planState.isAwaitingApproval()));
        renderedBusy=busy;renderedActivity=nextActivity;renderedTaskVersion=snapshot==null?Long.MIN_VALUE:snapshot.version;renderedPlanRevision=planState==null?Long.MIN_VALUE:planState.revision;
        if(visible){
            cancelFade();
            if(getVisibility()!=VISIBLE){setVisibility(VISIBLE);UiMotion.pageIn(this);}
            // 不忙、任务全部划掉、步骤全部完成：停留几秒后丝滑淡出，条子自己收走。
            if(!busy&&!holdingCompleted&&!hasOpenTasks&&allStepsDone()&&!(planState!=null&&(planState.isPlanning()||planState.isAwaitingApproval())))
                scheduleFade(STEPS_HOLD_MS);
        }
        else if(getVisibility()==VISIBLE)scheduleFade(STEPS_HOLD_MS);
        animating=busy||planState.isPlanning();arc.setAnimating(animating);arc.setAlpha(animating?1f:.28f);
        handler.removeCallbacks(tick);
        if(holdingCompleted&&isAttachedToWindow())handler.postDelayed(tick,Math.max(1L,completedHoldUntil-System.currentTimeMillis()));
        if(statusChanged||taskChanged||completedVisibilityChanged)renderStatus();if(taskChanged||completedVisibilityChanged)renderTasks();
    }

    /** 更新工作步骤列表（主线程安全）：全部完成且不忙时，停留几秒后整体淡出。 */
    public void setSteps(List<Step> next){
        if(Looper.myLooper()!=Looper.getMainLooper()){post(()->setSteps(next));return;}
        long now=System.currentTimeMillis();
        java.util.Set<String> stillActive=new HashSet<>();
        stepsList.clear();
        if(next!=null)for(Step s:next){
            Step copy=new Step(s.label);copy.done=s.done;stepsList.add(copy);
            if(s.done){stillActive.add(copy.label);stepDoneAt.putIfAbsent(copy.label,now);}
        }
        stepDoneAt.keySet().retainAll(stillActive);
        renderSteps();
        if(!stepsList.isEmpty()){
            if(renderedBusy||hasRunningSteps()){cancelFade();if(getVisibility()!=VISIBLE){setAlpha(1f);setVisibility(VISIBLE);}}
            else if(getVisibility()==VISIBLE&&!hasOpenTasks(snapshot))scheduleFade(STEPS_HOLD_MS);
        }
        else if(!renderedBusy&&getVisibility()==VISIBLE&&!hasOpenTasks(snapshot))scheduleFade(STEPS_HOLD_MS);
    }

    private boolean hasRunningSteps(){
        for(Step s:stepsList)if(!s.done)return true;
        return false;
    }

    private boolean allStepsDone(){
        for(Step s:stepsList)if(!s.done)return false;
        return true;
    }

    private void scheduleFade(long delay){
        if(stepsFadeAt>0L)return;
        stepsFadeAt=System.currentTimeMillis()+delay;
        handler.postDelayed(fadeOut,delay);
    }

    private void cancelFade(){
        if(stepsFadeAt==0L)return;
        stepsFadeAt=0L;
        handler.removeCallbacks(fadeOut);
        animate().cancel();
        setAlpha(1f);
    }

    /** 丝滑淡出：整体透明后真正隐藏并清掉步骤。 */
    private final Runnable fadeOut=new Runnable(){@Override public void run(){
        stepsFadeAt=0L;
        UiMotion.fadeOut(AgentProgressView.this,0f,480L,()->{
            stepsList.clear();renderSteps();
            setVisibility(GONE);
        });
    }};

    public void clear(){snapshot=null;planState=PlanWorkflowState.idle();animating=false;outputBacklog=0;arc.setSpeed(.55f);arc.setAnimating(false);completedHoldUntil=0L;handler.removeCallbacks(tick);handler.removeCallbacks(taskTtlTick);handler.removeCallbacks(stepTtlTick);completedTaskAt.clear();stepDoneAt.clear();tasks.removeAllViews();stepsList.clear();steps.removeAllViews();cancelFade();renderedTaskVersion=Long.MIN_VALUE;renderedPlanRevision=Long.MIN_VALUE;setVisibility(GONE);}

    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(tick);handler.removeCallbacks(fadeOut);handler.removeCallbacks(taskTtlTick);handler.removeCallbacks(stepTtlTick);super.onDetachedFromWindow();}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(completedHoldUntil>System.currentTimeMillis())handler.postDelayed(tick,completedHoldUntil-System.currentTimeMillis());
    }

    private final Runnable tick=new Runnable(){@Override public void run(){
        if(completedHoldUntil>0L&&System.currentTimeMillis()>=completedHoldUntil){
            completedHoldUntil=0L;
            renderTasks();renderStatus();
            if(!renderedBusy&&!hasOpenTasks(snapshot)&&stepsList.isEmpty()&&!(planState!=null&&(planState.isPlanning()||planState.isAwaitingApproval())))scheduleFade(COMPLETED_HOLD_MS);
        }
    }};

    private void render(){renderStatus();renderTasks();}

    /** 步骤行：1. 2. 3.…进行中高亮，完成后删除线并短暂停留，到点自动消失。 */
    private void renderSteps(){
        steps.removeAllViews();
        handler.removeCallbacks(stepTtlTick);
        long now=System.currentTimeMillis();long nextExpiry=0L;int visible=0;
        for(int i=0;i<stepsList.size();i++){
            Step s=stepsList.get(i);
            if(s.done){
                Long at=stepDoneAt.get(s.label);
                if(at!=null&&now-at>STEP_DONE_TTL_MS)continue; // 划掉超时的自动消失
                if(at!=null){long expiry=at+STEP_DONE_TTL_MS;if(expiry>now&&(nextExpiry==0L||expiry<nextExpiry))nextExpiry=expiry;}
            }
            if(visible>=5){
                if(!s.done||stepDoneAt.get(s.label)==null||now-stepDoneAt.get(s.label)<=STEP_DONE_TTL_MS)visible++; // 仅计数
                continue;
            }
            visible++;
            String label=visible+".  "+s.label;
            TextView row=rowText(label,s.done?mutedColor:accentColor);
            if(s.done){
                SpannableString span=new SpannableString(label);
                span.setSpan(new StrikethroughSpan(),0,span.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                row.setText(span);row.setAlpha(.72f);
            }
            steps.addView(row,new LayoutParams(-1,dp(24)));
        }
        if(visible>5)steps.addView(rowText("…  另有 "+(visible-5)+" 步",mutedColor),new LayoutParams(-1,dp(22)));
        if(nextExpiry>0L&&isAttachedToWindow())handler.postDelayed(stepTtlTick,Math.max(1L,nextExpiry-now));
    }

    /** 最早一条划掉的步骤到期：重渲染把它移除。 */
    private final Runnable stepTtlTick=new Runnable(){@Override public void run(){renderSteps();}};

    private void renderStatus(){
        String verb=currentVerb();
        boolean awaiting=planState!=null&&planState.isAwaitingApproval();
        boolean open=hasOpenTasks(snapshot);
        UiMotion.setTextCrossfade(status,verb);
        status.setTextColor(awaiting||animating?accentColor:open?textColor:mutedColor);
    }

    private String currentVerb(){
        if(planState!=null&&planState.isAwaitingApproval())return "计划等待确认…";
        if(planState!=null&&planState.isPlanning())return "正在规划…";
        if(hasCompletedTasks(snapshot)&&completedHoldUntil>System.currentTimeMillis())return "已完成";
        if(!renderedBusy&&hasOpenTasks(snapshot))return "待办 "+countOpenTasks(snapshot)+" 项";
        return fallbackStatus;
    }

    private static int countOpenTasks(TaskStore.Snapshot value){
        if(value==null)return 0;
        int count=0;for(JSONObject task:value.tasks)if(!"completed".equals(task.optString("status")))count++;
        return count;
    }

    private void renderTasks(){
        tasks.removeAllViews();if(snapshot==null)return;
        long now=System.currentTimeMillis();
        List<JSONObject> ordered=new ArrayList<>();Set<String> completed=new HashSet<>();for(JSONObject task:snapshot.tasks)if("completed".equals(task.optString("status")))completed.add(task.optString("id"));
        for(JSONObject task:snapshot.tasks)if("in_progress".equals(task.optString("status")))ordered.add(task);
        for(JSONObject task:snapshot.tasks)if("pending".equals(task.optString("status")))ordered.add(task);
        boolean showCompleted=hasOpenTasks(snapshot)||completedHoldUntil>System.currentTimeMillis();
        int completedShown=0;long nextExpiry=0L;
        if(showCompleted)for(JSONObject task:snapshot.tasks){
            if(!"completed".equals(task.optString("status")))continue;
            Long at=completedTaskAt.get(task.optString("id"));long doneAt=at==null?now:at;
            if(now-doneAt>TASK_DONE_TTL_MS)continue; // 划掉超时的自动消失
            ordered.add(task);completedShown++;
            long expiry=doneAt+TASK_DONE_TTL_MS;
            if(expiry>now&&(nextExpiry==0L||expiry<nextExpiry))nextExpiry=expiry;
        }
        int shown=Math.min(5,ordered.size());
        for(int i=0;i<shown;i++)tasks.addView(taskRow(ordered.get(i),completed,i==shown-1),new LayoutParams(-1,dp(27)));
        if(ordered.size()>shown){TextView more=rowText("└  另有 "+(ordered.size()-shown)+" 项",mutedColor);tasks.addView(more,new LayoutParams(-1,dp(24)));}
        handler.removeCallbacks(taskTtlTick);
        if(completedShown>0&&nextExpiry>0L&&isAttachedToWindow())handler.postDelayed(taskTtlTick,Math.max(1L,nextExpiry-now));
    }

    /** 最早一条划掉的行到期：重渲染让已消失的划掉行真正移除，空闲时顺带收起整个面板。 */
    private final Runnable taskTtlTick=new Runnable(){@Override public void run(){
        renderTasks();renderStatus();
        if(!renderedBusy&&!hasOpenTasks(snapshot)&&allStepsDone()&&stepsList.isEmpty()&&!(planState!=null&&(planState.isPlanning()||planState.isAwaitingApproval())))scheduleFade(STEPS_HOLD_MS);
    }};

    private static boolean hasOpenTasks(TaskStore.Snapshot value){
        if(value==null)return false;
        for(JSONObject task:value.tasks)if(!"completed".equals(task.optString("status")))return true;
        return false;
    }

    private static boolean hasCompletedTasks(TaskStore.Snapshot value){
        if(value==null)return false;
        for(JSONObject task:value.tasks)if("completed".equals(task.optString("status")))return true;
        return false;
    }

    private static boolean hasNewlyCompleted(TaskStore.Snapshot previous,TaskStore.Snapshot next){
        if(previous==null||next==null)return false;
        Set<String> oldCompleted=new HashSet<>();
        for(JSONObject task:previous.tasks)if("completed".equals(task.optString("status")))oldCompleted.add(task.optString("id"));
        for(JSONObject task:next.tasks)if("completed".equals(task.optString("status"))&&!oldCompleted.contains(task.optString("id")))return true;
        return false;
    }

    private View taskRow(JSONObject task,Set<String> completed,boolean last){
        String state=task.optString("status","pending");String subject=task.optString("subject","任务");
        JSONArray dependencies=task.optJSONArray("blockedBy");JSONArray openDependencies=new JSONArray();if(dependencies!=null)for(int i=0;i<dependencies.length();i++){String id=dependencies.optString(i,"");if(!id.isEmpty()&&!completed.contains(id))openDependencies.put(id);}boolean blocked=openDependencies.length()>0;
        String icon=last?"└─":"├─";
        String shown="in_progress".equals(state)&&!task.optString("activeForm","").trim().isEmpty()?task.optString("activeForm"):subject;
        if(blocked&&"pending".equals(state))shown+=" · 等待 "+join(openDependencies);
        int color="completed".equals(state)?greenColor:"in_progress".equals(state)?accentColor:blocked?mutedColor:textColor;
        TextView view=rowText(icon+"  "+shown,color);
        if("completed".equals(state)){SpannableString span=new SpannableString(view.getText());span.setSpan(new StrikethroughSpan(),4,span.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.setText(span);view.setAlpha(.72f);}
        view.setContentDescription("任务 "+task.optString("id","")+"，"+state+"，"+shown);return view;
    }

    private TextView rowText(String value,int color){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(10.7f);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setTypeface(Typeface.DEFAULT);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
    private static String join(JSONArray array){StringBuilder b=new StringBuilder();if(array!=null)for(int i=0;i<array.length();i++){if(b.length()>0)b.append(',');b.append('#').append(array.optString(i));}return b.toString();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private static final class ArcProgressView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Rect visibleBounds=new android.graphics.Rect();
        private final Runnable frame=new Runnable(){@Override public void run(){
            framePosted=false;
            if(!canAnimate()){stopFrames();return;}
            long now=android.os.SystemClock.uptimeMillis();
            long dt=lastFrameAt==0L?16L:Math.min(64L,now-lastFrameAt);
            lastFrameAt=now;
            angle=(angle+dt*.12f*speed)%360f;
            invalidate();syncFrames();
        }};
        private boolean animating,framePosted;
        private long lastFrameAt;
        private float angle,speed=.55f;
        private int color=0xff8fa58f;

        ArcProgressView(Context context){
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density);
        }

        void setColor(int value){color=value;invalidate();}
        void setSpeed(float value){speed=Math.max(.35f,Math.min(2f,value));invalidate();}
        void setAnimating(boolean value){animating=value;syncFrames();invalidate();}

        private boolean canAnimate(){
            if(!animating||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE||!isShown()||!UiMotion.enabled(getContext()))return false;
            float alpha=getAlpha();
            for(android.view.ViewParent parent=getParent();parent instanceof View;parent=parent.getParent())alpha*=((View)parent).getAlpha();
            return alpha>.05f&&getGlobalVisibleRect(visibleBounds);
        }

        private void syncFrames(){
            if(frame==null)return;
            if(!canAnimate()){stopFrames();return;}
            if(!framePosted){framePosted=true;postOnAnimation(frame);}
        }

        private void stopFrames(){removeCallbacks(frame);framePosted=false;lastFrameAt=0L;}

        // 父级淡入或滚回可见区域时恢复；监听绘制，不为隐藏视图轮询。
        private final android.view.ViewTreeObserver.OnPreDrawListener visibilityCheck=()->{syncFrames();return true;};
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnPreDrawListener(visibilityCheck);syncFrames();}
        @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnPreDrawListener(visibilityCheck);stopFrames();super.onDetachedFromWindow();}
        @Override protected void onVisibilityChanged(View changedView,int visibility){super.onVisibilityChanged(changedView,visibility);syncFrames();}
        @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncFrames();}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.34f;
            paint.setColor(color);paint.setAlpha(android.graphics.Color.alpha(color)*28/255);
            canvas.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,360f,false,paint);
            paint.setAlpha(android.graphics.Color.alpha(color));
            canvas.drawArc(cx-r,cy-r,cx+r,cy+r,angle-90f,84f,false,paint);
            syncFrames();
        }
    }
}
