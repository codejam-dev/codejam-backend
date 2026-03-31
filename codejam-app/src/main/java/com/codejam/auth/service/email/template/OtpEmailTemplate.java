package com.codejam.auth.service.email.template;

public class OtpEmailTemplate implements EmailTemplate {
    private final String otp;

    public OtpEmailTemplate(String otp) {
        this.otp = otp;
    }

    @Override
    public String getSubject() {
        return "🔐 Your OTP for CodeJam - " + otp;
    }

    @Override
    public String getHtmlBody() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OTP Verification</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f4f4f4;
                    }
                    .container {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        border-radius: 15px;
                        padding: 40px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .logo {
                        font-size: 28px;
                        font-weight: bold;
                        color: white;
                        margin-bottom: 10px;
                    }
                    .subtitle {
                        color: rgba(255,255,255,0.9);
                        font-size: 16px;
                    }
                    .content {
                        background: white;
                        border-radius: 10px;
                        padding: 30px;
                        margin: 20px 0;
                    }
                    .otp-container {
                        text-align: center;
                        margin: 30px 0;
                    }
                    .otp-code {
                        display: inline-block;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        font-size: 32px;
                        font-weight: bold;
                        padding: 20px 30px;
                        border-radius: 10px;
                        letter-spacing: 5px;
                        box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
                        margin: 20px 0;
                    }
                    .message {
                        font-size: 16px;
                        color: #555;
                        margin-bottom: 20px;
                        text-align: center;
                    }
                    .warning {
                        background: #fff3cd;
                        border: 1px solid #ffeaa7;
                        border-radius: 8px;
                        padding: 15px;
                        margin: 20px 0;
                        color: #856404;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 30px;
                        color: rgba(255,255,255,0.8);
                        font-size: 14px;
                    }
                    .security-tips {
                        background: #e8f4fd;
                        border-left: 4px solid #2196F3;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 0 8px 8px 0;
                    }
                    .security-tips h4 {
                        margin: 0 0 10px 0;
                        color: #1976D2;
                    }
                    .security-tips ul {
                        margin: 0;
                        padding-left: 20px;
                    }
                    .security-tips li {
                        margin: 5px 0;
                        color: #555;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">🚀 CodeJam</div>
                        <div class="subtitle">Your Coding Competition Platform</div>
                    </div>
                    
                    <div class="content">
                        <h2 style="text-align: center; color: #333; margin-bottom: 20px;">🔐 Email Verification Required</h2>
                        
                        <div class="message">
                            Thank you for registering with CodeJam! To complete your account setup, please use the OTP below to verify your email address.
                        </div>
                        
                        <div class="otp-container">
                            <div style="color: #666; margin-bottom: 10px;">Your verification code is:</div>
                            <div class="otp-code">{}</div>
                            <div style="color: #888; font-size: 14px; margin-top: 10px;">This code will expire in 10 minutes</div>
                        </div>
                        
                        <div class="warning">
                            <strong>⚠️ Important:</strong> Never share this OTP with anyone. Our team will never ask for your verification code.
                        </div>
                        
                        <div class="security-tips">
                            <h4>🛡️ Security Tips:</h4>
                            <ul>
                                <li>This OTP is valid for 10 minutes only</li>
                                <li>Use it immediately after receiving this email</li>
                                <li>If you didn't request this, please ignore this email</li>
                                <li>Keep your account credentials secure</li>
                            </ul>
                        </div>
                        
                        <div style="text-align: center; margin-top: 30px; color: #666;">
                            <p>If you're having trouble, please contact our support team.</p>
                        </div>
                    </div>
                    
                    <div class="footer">
                        <p>© 2024 CodeJam. All rights reserved.</p>
                        <p>This is an automated message, please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.replace("{}", otp);
    }
}