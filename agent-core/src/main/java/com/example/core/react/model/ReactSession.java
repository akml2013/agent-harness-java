package com.example.core.react.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.core.react.agent.TriggerSource;

import lombok.Data;

@Data
public class ReactSession {

    private String sessionId;
    private Long userId;
    private String userMessage;
    private Long taskId;

    private TriggerSource triggerSource = TriggerSource.USER;

    private SessionStatus status = SessionStatus.RUNNING;

    private volatile boolean aborted = false;

    private List<TaskItem> taskList = new ArrayList<>();
    private List<ReactStep> steps = new ArrayList<>();
    private boolean taskListFirstSent = false;
    private int lastSentCompletedCount = -1;
    private int consecutiveEmptyRounds = 0;

    private String lastParseError;

    private String executingWorkflowId;
    private String executingWorkflowNodeId;
    private String executingWorkflowExecutionId;
    private int executingWorkflowCompletedNodes = 0;
    private Map<String, String> workflowNodeMap = new ConcurrentHashMap<>();

    private int currentRound = 0;
    private int maxRounds = 10;
    private int continueAddRounds = 10;
    private String finalAnswer;
    private boolean finalAnswerHandled = false;

    private String pendingQuestion;
    private List<String> pendingOptions;

    public ReactSession(String sessionId, Long userId, String userMessage) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.userMessage = userMessage;
    }

    public boolean isFinished() {
        return status == SessionStatus.FINISHED;
    }

    public void setFinished(boolean finished) {
        if (finished) {
            status = SessionStatus.FINISHED;
        } else if (status == SessionStatus.FINISHED) {
            status = SessionStatus.RUNNING;
        }
    }

    public boolean isWaitingUserInput() {
        return status == SessionStatus.WAITING_USER_INPUT;
    }

    public void setWaitingUserInput(String question, List<String> options) {
        this.status = SessionStatus.WAITING_USER_INPUT;
        this.pendingQuestion = question;
        this.pendingOptions = options;
    }

    public void clearWaitingUserInput() {
        this.status = SessionStatus.RUNNING;
        this.pendingQuestion = null;
        this.pendingOptions = null;
    }

    public void addStep(ReactStep step) {
        step.setRound(currentRound);
        steps.add(step);
    }

    public ReactStep getLastStep() {
        if (steps.isEmpty())
            return null;
        return steps.get(steps.size() - 1);
    }

    public void incrementRound() {
        currentRound++;
    }

    public String getHistorySummary() {
        StringBuilder sb = new StringBuilder();
        for (ReactStep step : steps) {
            sb.append("=== 第").append(step.getRound() + 1).append("轮 ===\n");
            if (step.getThought() != null)
                sb.append("思考: ").append(step.getThought()).append("\n");
            if (step.getAction() != null)
                sb.append("动作: ").append(step.getAction()).append("\n");
            if (step.getActionInput() != null)
                sb.append("动作参数: ").append(step.getActionInput()).append("\n");
            if (step.getObservation() != null)
                sb.append("观察: ").append(step.getObservation()).append("\n");
            if (step.getDecision() != null)
                sb.append("决策: ").append(step.getDecision()).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getTaskListSummary() {
        if (taskList.isEmpty())
            return "当前无任务列表";
        StringBuilder sb = new StringBuilder("任务列表：\n");
        for (TaskItem item : taskList) {
            sb.append("  ").append(item.getItemIndex()).append(". [").append(item.getStatus()).append("] ")
                    .append(item.getDescription());
            sb.append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public void applyUserInput(String userInput, String selectedOption) {
        ReactStep lastStep = getLastStep();

        boolean isMaxRoundsPrompt = "已达到最大轮数限制，仍有未完成的任务。是否继续执行？".equals(pendingQuestion);

        if (isMaxRoundsPrompt) {
            if ("继续执行".equals(selectedOption)) {
                this.maxRounds += this.continueAddRounds;
                if (lastStep != null) {
                    lastStep.setObservation("用户选择继续执行，最大轮数已增加至" + this.maxRounds + "。请继续执行未完成的任务。");
                    lastStep.setDecision("CONTINUE");
                }
                clearWaitingUserInput();
            } else {
                if (lastStep != null) {
                    lastStep.setObservation("用户选择结束执行。");
                    lastStep.setDecision("FINISH");
                    lastStep.setFinished(true);
                }
                this.finalAnswer = "用户选择结束执行，任务已终止。";
                clearWaitingUserInput();
                this.setFinished(true);
            }
            return;
        }

        if (lastStep == null) {
            clearWaitingUserInput();
            return;
        }

        boolean isAskUserStep = "ask_user".equals(lastStep.getAction());

        if (isAskUserStep) {
            StringBuilder observation = new StringBuilder("用户反馈：");
            if (selectedOption != null && !selectedOption.isBlank()) {
                observation.append("用户选择了\"").append(selectedOption).append("\"");
            }
            if (userInput != null && !userInput.isBlank()) {
                if (selectedOption != null && !selectedOption.isBlank()) {
                    observation.append("；");
                }
                observation.append("用户输入：").append(userInput);
            }
            if ((selectedOption == null || selectedOption.isBlank()) && (userInput == null || userInput.isBlank())) {
                observation.append("用户未提供额外信息");
            }
            lastStep.setObservation(observation.toString());
            lastStep.setDecision("CONTINUE");
        }

        clearWaitingUserInput();
    }

    public void abort() {
        this.aborted = true;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void resetAborted() {
        this.aborted = false;
    }
}
