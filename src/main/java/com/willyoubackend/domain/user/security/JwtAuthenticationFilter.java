package com.willyoubackend.domain.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.willyoubackend.domain.user.dto.LoginRequestDto;
import com.willyoubackend.domain.user.entity.RefreshToken;
import com.willyoubackend.domain.user.entity.UserRoleEnum;
import com.willyoubackend.domain.user.jwt.JwtUtil;
import com.willyoubackend.domain.user.repository.RefreshTokenRepository;
import com.willyoubackend.global.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Slf4j(topic = "로그인 및 JWT 생성")
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RedisUtil redisUtil;

    //로그인
    public JwtAuthenticationFilter(JwtUtil jwtUtil, RefreshTokenRepository refreshTokenRepository, RedisUtil redisUtil) {
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
        this.redisUtil = redisUtil;
        setFilterProcessesUrl("/api/users/login");
    }

    // 로그인 토큰 생성
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {
            LoginRequestDto requestDto = new ObjectMapper().readValue(request.getInputStream(), LoginRequestDto.class);

            return getAuthenticationManager().authenticate(
                    new UsernamePasswordAuthenticationToken(
                            requestDto.getUsername(),
                            requestDto.getPassword(),
                            null
                    )
            );
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) {
        String username = ((UserDetailsImpl) authResult.getPrincipal()).getUsername();
        UserRoleEnum role = ((UserDetailsImpl) authResult.getPrincipal()).getUser().getRole();

        String token = jwtUtil.createToken(username, role);
        response.addHeader("Authorization", token);

        RefreshToken refreshToken = refreshTokenRepository.findByUsername(username).orElse(null);
        String refresh = jwtUtil.createRefreshToken(username, role);
        if (refreshToken == null) {
            refreshToken = new RefreshToken(refresh, username);
        } else {
            refreshToken.updateToken(refresh);
        }

        refreshTokenRepository.save(refreshToken);
        response.addHeader(JwtUtil.REFRESH_HEADER, refreshToken.getToken());

        // 유효 시간(5분)동안 {username, refreshToken} 저장
        redisUtil.setDataExpire(username, refresh, 3 * 60 * 1L); //3분

        // 로그인 성공시 "로그인 성공" 메시지를 반환
        response.setStatus(HttpServletResponse.SC_OK);
        writeResponse(response, "로그인 성공");
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) {
        // 로그인 실패시 "아이디 또는 비밀번호가 틀렸습니다." 메시지를 반환
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeResponse(response, "아이디 또는 비밀번호가 틀렸습니다.");
    }

    private void writeResponse(HttpServletResponse response, String message) {
        try {
            response.setContentType("text/plain");
            PrintWriter writer = response.getWriter();
            writer.write(message);
            writer.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}