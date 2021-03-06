package com.perfree.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.perfree.common.Constants;
import com.perfree.common.GravatarUtil;
import com.perfree.common.ResponseBean;
import com.perfree.model.Menu;
import com.perfree.model.Option;
import com.perfree.model.User;
import com.perfree.service.OptionService;
import com.perfree.service.SEOService;
import com.perfree.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * 控制首页地址
 */
@Controller
public class SystemController extends BaseController{
    private final Logger logger = LoggerFactory.getLogger(SystemController.class);
    @Autowired
    private UserService userService;
    @Autowired
    private OptionService optionService;
    @Autowired
    private SEOService seoService;

    /**
     * 后台首页
     * @return String
     */
    @RequestMapping("/admin")
    @RequiresRoles(value={"admin","superAdmin"}, logical= Logical.OR)
    public String adminIndex(Model model) {
        List<Menu> menus = getMenuByUserIdAndType();
        model.addAttribute("menus", menus);
        model.addAttribute("user", getUser());
        return view("static/admin/pages/index.html");
    }

    /**
     * 前台首页
     * @return String
     */
    @RequestMapping("/")
    public String index(Model model) {
        model.addAttribute("url", Constants.URL_ARTICLE_LIST);
        return view(currentThemePage() + "/index.html");
    }

    /**
     * 登陆页
     * @return String
     */
    @RequestMapping("/login")
    public String login() {
        return view("/login.html", "/login.html", "static/admin/pages/login/login.html");
    }

    /**
     * 注册页
     * @return String
     */
    @RequestMapping("/register")
    public String register() {
        return view("/register.html", "/register.html", "static/admin/pages/register/register.html");
    }

    /**
     * 登录
     * @return ResponseBean
     */
    @RequestMapping(method = RequestMethod.POST, path = "/doLogin")
    @ResponseBody
    public ResponseBean doLogin(@RequestBody User user,Boolean rememberMe, HttpSession session) {
        if(rememberMe == null) {
            rememberMe = false;
        }
        try {
            if (StringUtils.isBlank(user.getCaptcha()) ||
                    !user.getCaptcha().toUpperCase().equals(session.getAttribute("CAPTCHA_CODE").toString())){
                return ResponseBean.fail("验证码错误", null);
            }
            UsernamePasswordToken usernamePasswordToken = new UsernamePasswordToken(user.getAccount(),user.getPassword(),rememberMe);
            Subject subject = SecurityUtils.getSubject();
            subject.login(usernamePasswordToken);
            return ResponseBean.success("登录成功", null);
        }catch (IncorrectCredentialsException e) {
            return ResponseBean.fail("密码错误", e.getMessage());
        }catch (UnknownAccountException e) {
            return ResponseBean.fail("账户不存在", e.getMessage());
        }catch (Exception e) {
            return ResponseBean.fail("系统异常", e.getMessage());
        }
    }

    /**
     * 退出登录
     * @return ResponseBean
     */
    @RequestMapping(method = RequestMethod.GET, path = "/logout")
    public String logout() {
        Subject subject = SecurityUtils.getSubject();
        subject.logout();
        return view(currentThemePage() + "/index.html");
    }

    /**
     * 注册
     * @return ResponseBean
     */
    @RequestMapping(method = RequestMethod.POST, path = "/doRegister")
    @ResponseBody
    public ResponseBean doRegister(@RequestBody @Valid User user, HttpSession session) {
        Option optionByKey = optionService.getOptionByKey(Constants.OPTION_WEB_IS_REGISTER);
        if (optionByKey != null && optionByKey.getValue().equals(String.valueOf(Constants.REGISTER_NO))) {
            return ResponseBean.fail("网站已关闭注册功能", null);
        }
        if (StringUtils.isBlank(user.getCaptcha()) ||
                !user.getCaptcha().toUpperCase().equals(session.getAttribute("CAPTCHA_CODE").toString())){
            return ResponseBean.fail("验证码错误", null);
        }
        if (StringUtils.isBlank(user.getPassword()) || user.getPassword().length() < 6 ||
                user.getPassword().length() > 18){
            logger.error("密码不能为空且在6-18字符之间: {}", user.toString());
            return ResponseBean.fail("密码不能为空且在6-18字符之间", null);
        }
        if (userService.getUserByAccount(user.getAccount()) != null){
            logger.error("账户已存在: {}", user.toString());
            return ResponseBean.fail("账户已存在", null);
        }
        user.setStatus(0);
        user.setRoleId(2L);
        user.setAvatar(GravatarUtil.getGravatar(user.getEmail()));
        if (userService.add(user) > 0) {
            return ResponseBean.success("注册成功", null);
        }
        logger.error("注册失败: {}", user.toString());
        return ResponseBean.fail("注册失败", null);
    }

    /**
     * 验证码
     */
    @RequestMapping(method = RequestMethod.GET, path = "/captcha")
    public void captcha(String d,HttpSession session, HttpServletResponse response) throws IOException {
        if (StringUtils.isBlank(d)){
            response.sendRedirect("/captcha?d="+ Math.random() );
            return;
        }
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(200, 100,4,4);
        try {
            session.setAttribute("CAPTCHA_CODE", lineCaptcha.getCode().toUpperCase());
            response.setContentType("image/png");
            response.setHeader("Pragma", "No-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expire", 0);
            lineCaptcha.write(response.getOutputStream());
        }catch (Exception e){
            e.printStackTrace();
            logger.error("验证码生成失败: {}", e.getMessage());
        }
    }

    @GetMapping("/robots.txt")
    public void robotsTxt(HttpServletResponse response) throws IOException {
        Writer writer = response.getWriter();
        String lineSeparator = System.getProperty("line.separator", "\n");
        writer.append("User-agent: *").append(lineSeparator);
        writer.append("Disallow:").append("/admin/*").append(lineSeparator);
    }

    @GetMapping("/sitemap.xml")
    public void createSiteMapXml(HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        Writer writer = response.getWriter();
        writer.append(seoService.createSiteMapXmlContent());
    }
}
