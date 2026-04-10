package com.hewei.hzyjy.xunzhi.interview.service;

import cn.hutool.core.util.StrUtil;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ServiceException;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewQuestion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InterviewResumePreviewService {

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final InterviewQuestionService interviewQuestionService;

    public ResumePreviewResource loadResumePreview(String sessionId) {
        InterviewQuestion question = interviewQuestionService.getBySessionId(sessionId);
        if (question == null) {
            throw new ClientException("Interview session not found");
        }

        String resumeFileUrl = question.getResumeFileUrl();
        if (StrUtil.isBlank(resumeFileUrl)) {
            throw new ClientException("Resume preview source is missing");
        }

        Request request = new Request.Builder()
                .url(resumeFileUrl.trim())
                .get()
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ServiceException("Failed to download resume preview, HTTP status: " + response.code());
            }
            if (response.body() == null) {
                throw new ServiceException("Failed to download resume preview: empty body");
            }

            byte[] content = response.body().bytes();
            if (content.length == 0) {
                throw new ServiceException("Failed to download resume preview: empty content");
            }

            String fileName = resolveFileName(resumeFileUrl, sessionId);
            String contentType = resolveContentType(content, response.header("Content-Type"));
            return new ResumePreviewResource(content, contentType, fileName);
        } catch (ClientException | ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to download resume preview: " + ex.getMessage());
        }
    }

    private String resolveFileName(String sourceUrl, String sessionId) {
        HttpUrl parsedUrl = HttpUrl.parse(sourceUrl);
        if (parsedUrl != null && parsedUrl.pathSize() > 0) {
            String lastSegment = parsedUrl.pathSegments().get(parsedUrl.pathSize() - 1);
            if (StrUtil.isNotBlank(lastSegment)) {
                return lastSegment.endsWith(".pdf") ? lastSegment : lastSegment + ".pdf";
            }
        }
        return "resume-" + sessionId + ".pdf";
    }

    private String resolveContentType(byte[] content, String responseContentType) {
        if (isPdf(content)) {
            return "application/pdf";
        }
        return StrUtil.blankToDefault(responseContentType, "application/octet-stream");
    }

    private boolean isPdf(byte[] content) {
        return content != null
                && content.length >= 5
                && content[0] == 0x25
                && content[1] == 0x50
                && content[2] == 0x44
                && content[3] == 0x46
                && content[4] == 0x2D;
    }

    @Getter
    public static final class ResumePreviewResource {
        private final byte[] content;
        private final String contentType;
        private final String fileName;

        public ResumePreviewResource(byte[] content, String contentType, String fileName) {
            this.content = content;
            this.contentType = contentType;
            this.fileName = fileName;
        }
    }
}
