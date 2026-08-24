package com.mimo.dto;

import lombok.Data;

import java.util.Date;

public class MailDTO {

    @Data
    public static class MailSummaryVO {
        public Integer messageSeq;   // INBOX 中的序号（前端用来取详情）
        public String subject;
        public String from;
        public String fromPersonal;
        public Date sentDate;
        public String snippet;
        public boolean seen;
        public long size;
    }

    @Data
    public static class MailDetailVO {
        public String subject;
        public String from;
        public String fromPersonal;
        public String to;
        public Date sentDate;
        public String textBody;
        public String htmlBody;
    }

    @Data
    public static class InboxVO {
        public EmailAccountDTO.AccountVO account;
        public java.util.List<MailSummaryVO> messages;
    }
}
