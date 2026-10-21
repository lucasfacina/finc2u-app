package br.com.finc2u.server.config;

import br.com.finc2u.server.features.recurringexpense.job.RecurringExpenseJob;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// A chave app.scheduling.enabled liga ou desliga o agendamento inteiro: sem ela, nem o job existe.
class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SchedulingConfig.class, RecurringExpenseJob.class)
        .withBean(RecurringExpenseService.class, () -> mock(RecurringExpenseService.class))
        .withBean(RecurringExpenseMaterializer.class, () -> mock(RecurringExpenseMaterializer.class))
        .withBean("clock", Clock.class, Clock::systemDefaultZone);

    @Test
    void shouldRegisterTheJobAndSchedulingWhenEnabled() {
        contextRunner.withPropertyValues("app.scheduling.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(RecurringExpenseJob.class);
                assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            });
    }

    @Test
    void shouldNotRegisterTheJobNorSchedulingWhenDisabled() {
        contextRunner.withPropertyValues("app.scheduling.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(RecurringExpenseJob.class);
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            });
    }

    @Test
    void shouldNotRegisterTheJobNorSchedulingWhenThePropertyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(RecurringExpenseJob.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

}
