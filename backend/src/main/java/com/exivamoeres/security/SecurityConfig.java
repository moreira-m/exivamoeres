package com.exivamoeres.security;

import com.exivamoeres.config.ActuatorProperties;
import com.exivamoeres.security.oauth.CustomOAuth2UserService;
import com.exivamoeres.security.oauth.CustomOidcUserService;
import com.exivamoeres.security.oauth.OAuth2LoginSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Header do token dedicado do Actuator. Não é `Authorization` de propósito:
     * isto não é um usuário do produto, é um coletor de métricas.
     */
    static final String ACTUATOR_TOKEN_HEADER = "X-Actuator-Token";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final String allowedOrigin;
    private final ActuatorProperties actuatorProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          CustomOAuth2UserService customOAuth2UserService,
                          CustomOidcUserService customOidcUserService,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          @Value("${app.cors.allowed-origin}") String allowedOrigin,
                          ActuatorProperties actuatorProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.allowedOrigin = allowedOrigin;
        this.actuatorProperties = actuatorProperties;
    }

    /**
     * Cadeia própria do Actuator, declarada ANTES da cadeia da API.
     *
     * O motivo de existir: métricas não são dado de usuário. Enquanto
     * `/actuator/**` caía no `anyRequest().authenticated()` da API, qualquer
     * conta — inclusive uma anônima criada em um segundo — lia
     * `/actuator/metrics` e `/actuator/prometheus`, ou seja, volume de usuários,
     * rotas mais chamadas, taxa de erro e o mapa interno da aplicação.
     *
     * Aqui a autenticação de usuário não vale nada: só `health` é público (é o
     * healthcheck do Railway e do docker-compose) e o resto exige o token
     * dedicado do {@code ACTUATOR_TOKEN}. Sem token configurado — o padrão —
     * ninguém acessa nada além do health.
     *
     * Isto é a segunda camada: por padrão o `application.yml` nem expõe
     * `metrics`/`prometheus`. As duas juntas evitam a armadilha de ligar
     * `ACTUATOR_ENDPOINTS` em produção e reabrir o vazamento sem perceber.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(comTokenDoActuator()).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(handler ->
                        handler.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    /**
     * Compara o header com o token configurado em tempo constante
     * ({@link MessageDigest#isEqual}) — comparação de segredo por `equals`
     * vaza o prefixo correto pelo tempo de resposta.
     */
    private RequestMatcher comTokenDoActuator() {
        String esperado = actuatorProperties.token();
        return request -> {
            String apresentado = request.getHeader(ACTUATOR_TOKEN_HEADER);
            if (!StringUtils.hasText(esperado) || apresentado == null) {
                return false;
            }
            return MessageDigest.isEqual(
                    apresentado.getBytes(StandardCharsets.UTF_8),
                    esperado.getBytes(StandardCharsets.UTF_8));
        };
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless com JWT: sem sessão, sem CSRF (não há cookies de sessão).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Fluxo OAuth2 (redirects do Spring Security)
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // `/actuator/**` não chega aqui: é tratado pela
                        // actuatorFilterChain, que roda antes desta.
                        .requestMatchers("/error").permitAll()
                        // Handshake do chat: a autenticação real acontece no
                        // frame STOMP CONNECT (StompAuthChannelInterceptor).
                        .requestMatchers("/ws/**").permitAll()
                        // "/api/lists/mine" precisa de auth — declarado ANTES do
                        // curinga público para não ser capturado por ele.
                        .requestMatchers(HttpMethod.GET, "/api/lists/mine").authenticated()
                        // Área pública (home sem login): buscar times, ver
                        // detalhe, listar worlds e criaturas para os filtros.
                        .requestMatchers(HttpMethod.GET, "/api/lists/search", "/api/lists/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/worlds", "/api/creatures").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler ->
                        // Sem token válido = 401 puro, sem redirect pra página de login.
                        handler.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                // .userService cobre OAuth2 comum (Discord); providers OIDC
                                // (Google, por causa do scope "openid") são roteados por
                                // .oidcUserService — os dois precisam ser registrados.
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                        .successHandler(oAuth2LoginSuccessHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Origem única e explícita (env FRONTEND_URL) — nunca "*" em produção.
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
