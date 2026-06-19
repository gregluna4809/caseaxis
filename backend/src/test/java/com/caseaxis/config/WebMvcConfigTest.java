package com.caseaxis.config;

import com.caseaxis.security.LoginRateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class WebMvcConfigTest {

    @Test
    void pageableCustomizerCapsRequestedPageSizeAtTwoHundred() throws Exception {
        WebMvcConfig config = new WebMvcConfig(mock(LoginRateLimitInterceptor.class));
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        config.pageableCustomizer().customize(resolver);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "0");
        request.addParameter("size", "999");

        Pageable pageable = (Pageable) resolver.resolveArgument(
            pageableParameter(),
            null,
            new ServletWebRequest(request),
            null
        );

        assertEquals(200, pageable.getPageSize());
    }

    private MethodParameter pageableParameter() throws NoSuchMethodException {
        Method method = WebMvcConfigTest.class.getDeclaredMethod("pageableEndpoint", Pageable.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void pageableEndpoint(Pageable pageable) {
    }
}
