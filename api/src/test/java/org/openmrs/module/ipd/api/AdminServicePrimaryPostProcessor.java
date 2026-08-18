package org.openmrs.module.ipd.api;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Marks 'adminService' as the primary AdministrationService bean so that Spring 5.3+
 * can resolve @Autowired AdministrationService injection unambiguously in integration tests
 * (avoids NoUniqueBeanDefinitionException caused by both adminService and adminServiceTarget).
 */
public class AdminServicePrimaryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory.containsBeanDefinition("adminService")) {
            BeanDefinition bd = beanFactory.getBeanDefinition("adminService");
            bd.setPrimary(true);
        }
    }
}
