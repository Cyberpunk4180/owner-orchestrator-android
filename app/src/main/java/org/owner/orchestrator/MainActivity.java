package org.owner.orchestrator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-only control plane; all long-running work remains on the independent controller. */
public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(17, 29, 46);
    private static final int CYAN = Color.rgb(20, 96, 132);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SecureStore store;
    private EditText endpointInput;
    private EditText tokenInput;
    private TextView status;
    private LinearLayout tasks;
    private boolean visible = false;
    private boolean refreshPending = false;

    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            if (!store.baseUrl().isEmpty() && !refreshPending) refreshTasks(false);
            main.postDelayed(this, 20_000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new SecureStore(this);
        render();
        handleShare(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private void handleShare(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) &&
                "text/plain".equals(intent.getType())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                String value=shared.toString();
                main.post(() -> askForJob(value.length()>12000?value.substring(0,12000):value));
            }
        }
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    private LinearLayout column() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0,dp(4),0,dp(4));
        return view;
    }

    private Button button(String label, LinearLayout parent, Runnable click) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false);
        b.setTextColor(NAVY);
        b.setOnClickListener(v -> click.run());
        parent.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245,247,251));
        LinearLayout panel = column();
        panel.setPadding(dp(18),dp(24),dp(18),dp(28));
        scroll.addView(panel);
        setContentView(scroll);

        panel.addView(text("OWNER ORCHESTRATOR",24,NAVY,true));
        panel.addView(text("Управление постоянными задачами · Android",14,MUTED,false));
        status = text("Сервер не проверен",14,CYAN,true);
        panel.addView(status);

        panel.addView(text("HTTPS-адрес оркестратора",16,NAVY,true));
        endpointInput = new EditText(this);
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setHint("https://orchestrator.example.com");
        endpointInput.setText(store.baseUrl());
        panel.addView(endpointInput);

        panel.addView(text("Токен администратора (Android Keystore)",16,NAVY,true));
        tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setHint("Введите новый токен; пустое поле сохранит прежний");
        panel.addView(tokenInput);

        button("Сохранить подключение",panel,this::saveConnection);
        button("Удалить сохранённый токен",panel,() -> new AlertDialog.Builder(this)
                .setTitle("Удалить токен?")
                .setMessage("Приложение перестанет обращаться к защищённому API до следующей авторизации.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)->{
                    store.clearToken();tokenInput.setText("");setStatus("Токен удалён");
                }).show());
        button("Проверить сервер",panel,()->runRequest("Проверка сервера",api -> api.get("/health"),
                result -> setStatus("Сервер доступен ✓")));

        panel.addView(text("Задания",19,NAVY,true));
        button("↻ Обновить очередь",panel,()->refreshTasks(true));
        button("+ Создать ИИ-задание",panel,this::askForJob);
        button("Открыть обычный ChatGPT",panel,() -> {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"));
            startActivity(browser);
        });
        panel.addView(text("Выполнение не зависит от того, открыто ли приложение. ИИ-задания ожидают отдельного авторизованного исполнителя.",13,MUTED,false));
        tasks = column();
        panel.addView(tasks);
        if (!store.baseUrl().isEmpty()) refreshTasks(false);
    }

    private void setStatus(String message) { status.setText(message); }
    private void saveConnection() {
        try {
            String value = OrchestratorApi.validateEndpoint(endpointInput.getText().toString());
            String newToken = tokenInput.getText().toString().trim();
            if (!newToken.isEmpty()) store.setToken(newToken);
            if (store.token().isEmpty()) throw new IllegalStateException("Введите токен администратора");
            store.setBaseUrl(value);
            tokenInput.setText("");
            setStatus("Подключение сохранено. Проверяю доступ...");
            refreshTasks(true);
        } catch (Exception ex) { showError(ex); }
    }

    interface JobCall { JSONObject run(OrchestratorApi api) throws Exception; }
    interface JobResult { void show(JSONObject data) throws Exception; }

    private void runRequest(String label, JobCall command, JobResult callback) {
        String server = store.baseUrl();
        if (server.isEmpty()) { setStatus("Сначала сохраните HTTPS-адрес и токен"); return; }
        setStatus(label + "...");
        requests.execute(() -> {
            try {
                String secret = store.token();
                JSONObject output = command.run(new OrchestratorApi(server,secret));
                main.post(() -> {
                    try { callback.show(output); }
                    catch (Exception err) { showError(err); }
                });
            } catch (Exception ex) { main.post(() -> showError(ex)); }
        });
    }

    private void showError(Exception ex) {
        // No credentials, stack traces or request headers are ever shown/logged.
        String msg = ex.getMessage() == null ? "Неизвестная ошибка" : ex.getMessage();
        setStatus(msg.length() > 180 ? msg.substring(0,180) : msg);
    }

    private void refreshTasks(boolean explicit) {
        if (refreshPending) return;
        refreshPending = true;
        String server = store.baseUrl();
        if (server.isEmpty()) {refreshPending=false;return;}
        if (explicit) setStatus("Загружаю задания...");
        requests.execute(() -> {
            try {
                JSONObject response = new OrchestratorApi(server,store.token()).get("/v1/jobs");
                main.post(() -> {
                    refreshPending=false;
                    renderTasks(response.optJSONArray("jobs"));
                    setStatus("Связь установлена · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
                });
            } catch (Exception ex) {main.post(() -> { refreshPending=false; showError(ex);});}
        });
    }

    private void renderTasks(JSONArray jobs) {
        tasks.removeAllViews();
        if (jobs == null || jobs.length() == 0) {
            tasks.addView(text("Очередь пуста",14,MUTED,false));return;
        }
        for (int i=0;i<jobs.length();i++) {
            JSONObject job=jobs.optJSONObject(i);
            if (job==null)continue;
            String id=job.optString("id", "");
            String state=job.optString("status", "unknown");
            String kind=job.optString("kind", "task");
            JSONObject payload=job.optJSONObject("payload");
            String goal=payload!=null?payload.optString("goal",""):"";
            if (goal.length()>180)goal=goal.substring(0,180)+"…";
            LinearLayout card=column();
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin=dp(10);tasks.addView(card,lp);
            card.addView(text(kind+" · "+state,16,NAVY,true));
            if (!goal.isEmpty())card.addView(text(goal,14,Color.DKGRAY,false));
            card.addView(text("Попытки: "+job.optInt("attempts",0)+" / "+job.optInt("max_attempts",5),13,MUTED,false));
            button("Подробности и события",card,()->showJob(id));
            if (state.equals("queued") || state.equals("running")) {
                button("Отменить задание",card,()->new AlertDialog.Builder(this)
                    .setTitle("Отменить задание?")
                    .setMessage("Задание: "+id+". Выполняющий агент должен отдельно обработать отмену.")
                    .setNegativeButton("Нет",null)
                    .setPositiveButton("Отменить",(d,w)->runRequest("Отмена", api -> api.post("/v1/jobs/"+id+"/cancel",new JSONObject()),r->refreshTasks(true)))
                    .show());
            }
        }
    }

    private void showJob(String id) {
        runRequest("Подробности",api->api.get("/v1/jobs/"+id),response->{
            JSONObject job=response.optJSONObject("job");
            JSONArray events=response.optJSONArray("events");
            StringBuilder details=new StringBuilder();
            if (job!=null) details.append(job.toString(2));
            details.append("\n\nСОБЫТИЯ:\n");
            if (events!=null) details.append(events.toString(2));
            new AlertDialog.Builder(this).setTitle("Состояние задания")
                .setMessage(details.length()>14_000?details.substring(0,14_000):details.toString())
                .setPositiveButton("Закрыть",null).show();
            setStatus("Данные загружены");
        });
    }

    private void askForJob() { askForJob(""); }

    private void askForJob(String prefilled) {
        EditText input=new EditText(this);
        input.setText(prefilled);
        input.setHint("Что необходимо сделать? Например: проверить PR и подготовить отчёт о тестах.");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(4);input.setMaxLines(10);
        LinearLayout holder=column();holder.setPadding(dp(20),0,dp(20),0);holder.addView(input);
        new AlertDialog.Builder(this)
            .setTitle("Новое ИИ-задание")
            .setView(holder)
            .setMessage("Задание сохраняется в очереди. Для исполнения нужен отдельный авторизованный ИИ-агент.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Создать",(d,w)->{
                String goal=input.getText().toString().trim();
                if(goal.isEmpty()||goal.length()>12000){setStatus("Введите цель длиной от 1 до 12000 символов");return;}
                runRequest("Создание задания", api ->{
                    JSONObject p=new JSONObject();p.put("goal",goal);
                    JSONObject body=new JSONObject();body.put("kind","agent_task");body.put("payload",p);
                    return api.post("/v1/jobs",body);
                },result->{
                    JSONObject job=result.optJSONObject("job");
                    setStatus(job!=null?"Добавлено в очередь: "+job.optString("id"):"Задание принято");
                    refreshTasks(false);
                });
            }).show();
    }

    @Override protected void onStart() {super.onStart();visible=true;main.removeCallbacks(periodic);main.postDelayed(periodic,20_000);}
    @Override protected void onStop() {visible=false;main.removeCallbacks(periodic);super.onStop();}
    @Override protected void onDestroy() {requests.shutdownNow();super.onDestroy();}
}
