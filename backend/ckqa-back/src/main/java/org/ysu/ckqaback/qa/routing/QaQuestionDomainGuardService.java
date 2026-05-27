package org.ysu.ckqaback.qa.routing;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckRequest;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.Objects;

/**
 * 问题领域硬拦截。
 * <p>
 * 只识别明显不属于课程资料问答的请求；弱课程意图或上下文追问默认放行，避免误伤。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class QaQuestionDomainGuardService {

    private final QaSessionsService qaSessionsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private CourseAccessService courseAccessService;

    @Autowired(required = false)
    public void setCourseAccessService(CourseAccessService courseAccessService) {
        this.courseAccessService = courseAccessService;
    }

    public QaQuestionDomainCheckResponse check(QaQuestionDomainCheckRequest request, AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "请求不能为空");
        }

        resolveScope(request, currentUser);
        return classify(request.getQuestion());
    }

    private QaQuestionDomainCheckResponse classify(String question) {
        String normalized = normalize(question);
        if (isCampusLife(normalized)) {
            return QaQuestionDomainCheckResponse.outOfScope("campus_life", "这个问题更像校园生活咨询，不属于当前课程资料问答范围。");
        }
        if (isCreativeWriting(normalized)) {
            return QaQuestionDomainCheckResponse.outOfScope("creative_writing", "这个问题更像创作写作请求，不属于当前课程资料问答范围。");
        }
        if (isProfileHelp(normalized)) {
            return QaQuestionDomainCheckResponse.outOfScope("profile_help", "这个问题更像个人资料操作帮助，不属于当前课程资料问答范围。");
        }
        if (isCourseAdministrivia(normalized)) {
            return QaQuestionDomainCheckResponse.outOfScope("course_administrivia", "这个问题更像课程事务安排咨询，不属于当前课程资料问答范围。");
        }
        return QaQuestionDomainCheckResponse.allowed();
    }

    private void resolveScope(QaQuestionDomainCheckRequest request, AuthenticatedUser currentUser) {
        if (request.getSessionId() != null) {
            QaSessions session = qaSessionsService.getRequiredById(request.getSessionId());
            if (!currentUser.id().equals(session.getUserId())) {
                throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能访问自己的问答会话");
            }
            validateSessionRequestScope(request, session);
            String courseId = effectiveSessionCourseId(request, session);
            if (session.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
                validateKnowledgeBaseScope(courseId, knowledgeBase, currentUser);
            } else if (request.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
                validateKnowledgeBaseScope(courseId, knowledgeBase, currentUser);
            } else if (StringUtils.hasText(courseId)) {
                validateCourseReadable(courseId, currentUser);
            }
            return;
        }

        if (request.getKnowledgeBaseId() != null) {
            KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
            validateKnowledgeBaseScope(request.getCourseId(), knowledgeBase, currentUser);
            return;
        }

        if (StringUtils.hasText(request.getCourseId())) {
            validateCourseReadable(request.getCourseId(), currentUser);
        }
    }

    private void validateSessionRequestScope(QaQuestionDomainCheckRequest request, QaSessions session) {
        if (StringUtils.hasText(request.getCourseId())
                && StringUtils.hasText(session.getCourseId())
                && !Objects.equals(request.getCourseId(), session.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "会话不属于当前课程");
        }
        if (request.getKnowledgeBaseId() != null
                && session.getKnowledgeBaseId() != null
                && !Objects.equals(request.getKnowledgeBaseId(), session.getKnowledgeBaseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "会话不属于当前知识库");
        }
    }

    private String effectiveSessionCourseId(QaQuestionDomainCheckRequest request, QaSessions session) {
        if (StringUtils.hasText(session.getCourseId())) {
            return session.getCourseId();
        }
        return request.getCourseId();
    }

    private void validateKnowledgeBaseScope(String courseId, KnowledgeBases knowledgeBase, AuthenticatedUser currentUser) {
        if (StringUtils.hasText(courseId) && !Objects.equals(courseId, knowledgeBase.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "知识库不属于当前课程");
        }
        validateCourseReadable(knowledgeBase.getCourseId(), currentUser);
    }

    private void validateCourseReadable(String courseId, AuthenticatedUser currentUser) {
        if (courseAccessService != null && StringUtils.hasText(courseId)) {
            courseAccessService.assertCourseReadable(courseId, currentUser.userCode());
        }
    }

    private boolean isCampusLife(String text) {
        boolean dining = containsAny(text, "吃什么", "吃啥", "吃点什么", "有什么菜", "晚饭吃", "晚餐吃", "午饭吃", "早餐吃", "夜宵吃", "点外卖")
                || (text.contains("食堂")
                && containsAny(text, "菜", "饭", "吃", "菜单", "窗口")
                && containsAny(text, "今天", "今晚", "晚上", "中午", "早上", "明天", "现在", "有什么", "推荐"));
        boolean campusCardHelp = text.contains("校园卡")
                && containsAny(text, "充值", "余额", "挂失", "补办", "丢了", "怎么用", "刷卡", "扣费");
        boolean dormLife = text.contains("宿舍")
                && containsAny(text, "报修", "水电", "门禁", "熄灯", "床位", "空调", "几号楼");
        return dining || campusCardHelp || dormLife;
    }

    private boolean isCreativeWriting(String text) {
        boolean writingIntent = containsAny(text, "帮我写", "写一首", "写一篇", "创作", "生成一首");
        boolean creativeObject = containsAny(text, "短诗", "诗歌", "歌词", "故事", "小说", "散文", "情书", "文案");
        return writingIntent && creativeObject;
    }

    private boolean isProfileHelp(String text) {
        return text.contains("头像") && containsAny(text, "怎么换", "如何换", "更换", "修改", "设置", "上传");
    }

    private boolean isCourseAdministrivia(String text) {
        boolean administrivia = containsAny(text, "期末考试", "考试时间", "考试安排", "补考", "上课时间", "教室", "签到", "点名", "成绩");
        boolean scheduling = containsAny(text, "什么时候", "哪天", "几点", "时间", "安排", "地点", "在哪里", "怎么查");
        boolean assignmentDeadline = containsAny(text, "作业什么时候交", "什么时候交作业", "作业截止");
        return assignmentDeadline || (administrivia && scheduling);
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }
}
