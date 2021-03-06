package com.cyquen.presentcloud.config;

import com.cyquen.presentcloud.entity.ResponseBean;
import com.cyquen.presentcloud.security.CurrentUserDetails;
import com.cyquen.presentcloud.security.JwtAuthenticationProcessingFilter;
import com.cyquen.presentcloud.security.LoginFilter;
import com.cyquen.presentcloud.security.jwt.AccessToken;
import com.cyquen.presentcloud.security.jwt.TokenService;
import com.cyquen.presentcloud.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.PrintWriter;

/**
 * @author Zhengxikun
 */
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private UserDetailsServiceImpl userService;
    private JwtAuthenticationProcessingFilter jwtAuthenticationProcessingFilter;
    private TokenService tokenService;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public SecurityConfig(UserDetailsServiceImpl userService, JwtAuthenticationProcessingFilter jwtAuthenticationProcessingFilter,
                          TokenService tokenService) {
        this.userService = userService;
        this.jwtAuthenticationProcessingFilter = jwtAuthenticationProcessingFilter;
        this.tokenService = tokenService;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userService);
    }

    @Override
    public void configure(WebSecurity web) {
        web.ignoring().mvcMatchers("/register", "/sms");
    }

    @Bean
    LoginFilter loginFilter() throws Exception {
        LoginFilter loginFilter = new LoginFilter();
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json;charset=utf-8");
                    PrintWriter out = response.getWriter();
                    CurrentUserDetails user = (CurrentUserDetails) authentication.getPrincipal();
                    AccessToken accessToken = tokenService.createToken(user, 7);
                    ResponseBean ok = ResponseBean.ok("????????????!", accessToken);
                    String s = new ObjectMapper().writeValueAsString(ok);
                    out.write(s);
                    out.flush();
                    out.close();
                }
        );
        loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
                    response.setContentType("application/json;charset=utf-8");
                    PrintWriter out = response.getWriter();
                    ResponseBean respBean = ResponseBean.error(exception.getMessage());
                    if (exception instanceof LockedException) {
                        respBean.setMsg("????????????????????????????????????!");
                    } else if (exception instanceof CredentialsExpiredException) {
                        respBean.setMsg("?????????????????????????????????!");
                    } else if (exception instanceof AccountExpiredException) {
                        respBean.setMsg("?????????????????????????????????!");
                    } else if (exception instanceof DisabledException) {
                        respBean.setMsg("????????????????????????????????????!");
                    } else if (exception instanceof BadCredentialsException) {
                        respBean.setMsg("???????????????????????????????????????????????????!");
                    }
                    out.write(new ObjectMapper().writeValueAsString(respBean));
                    out.flush();
                    out.close();
                }
        );
        loginFilter.setAuthenticationManager(authenticationManagerBean());
        loginFilter.setFilterProcessesUrl("/doLogin");
        return loginFilter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.logout()
                .logoutSuccessHandler(((request, response, authentication) -> {
                    response.setContentType("application/json;charset=utf-8");
                    PrintWriter out = response.getWriter();
                    out.write(new ObjectMapper().writeValueAsString(ResponseBean.ok("????????????")));
                    out.flush();
                    out.close();
                }))
                .and()
                .csrf().disable().exceptionHandling()
                .accessDeniedHandler(((request, response, accessDeniedException) -> {
                    response.setContentType("application/json;charset=utf-8");
                    response.setStatus(401);
                    PrintWriter out = response.getWriter();
                    ResponseBean respBean = ResponseBean.error("????????????");
                    out.write(new ObjectMapper().writeValueAsString(respBean));
                    out.flush();
                    out.close();
                }))
                .authenticationEntryPoint(((request, response, authException) -> {
                    response.setContentType("application/json;charset=utf-8");
                    response.setStatus(400);
                    PrintWriter out = response.getWriter();
                    ResponseBean respBean = ResponseBean.error("????????????");
                    if (authException instanceof InsufficientAuthenticationException) {
                        respBean.setMsg(authException.getMessage());
                    }
                    out.write(new ObjectMapper().writeValueAsString(respBean));
                    out.flush();
                    out.close();
                }));
        http.addFilterBefore(jwtAuthenticationProcessingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAt(loginFilter(), UsernamePasswordAuthenticationFilter.class);
        http.cors();
        http.authorizeRequests().anyRequest().authenticated();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
