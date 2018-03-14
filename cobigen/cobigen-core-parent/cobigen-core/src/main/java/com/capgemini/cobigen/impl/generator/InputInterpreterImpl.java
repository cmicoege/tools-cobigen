package com.capgemini.cobigen.impl.generator;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import com.capgemini.cobigen.api.InputInterpreter;
import com.capgemini.cobigen.api.annotation.Cached;
import com.capgemini.cobigen.api.exception.CobiGenRuntimeException;
import com.capgemini.cobigen.api.exception.InputReaderException;
import com.capgemini.cobigen.api.exception.PluginNotAvailableException;
import com.capgemini.cobigen.api.extension.InputReader;
import com.capgemini.cobigen.api.extension.TriggerInterpreter;
import com.capgemini.cobigen.impl.config.entity.Trigger;
import com.capgemini.cobigen.impl.extension.PluginRegistry;
import com.capgemini.cobigen.impl.generator.api.InputResolver;
import com.capgemini.cobigen.impl.generator.api.TriggerMatchingEvaluator;

/**
 * Implementation of the CobiGen API for input processing
 */
public class InputInterpreterImpl implements InputInterpreter {

    /** Configuration interpreter instance */
    @Inject
    private TriggerMatchingEvaluator configurationInterpreter;

    /** {@link InputResolver} instance */
    @Inject
    private InputResolver inputResolver;

    @Cached
    @Override
    public boolean combinesMultipleInputs(Object input) {
        List<Trigger> matchingTriggers = configurationInterpreter.getMatchingTriggers(input);
        return matchingTriggers.stream().anyMatch(e -> e.matchesByContainerMatcher());
    }

    @Cached
    @Override
    public List<Object> resolveContainers(Object input) {
        List<Trigger> matchingTriggers = configurationInterpreter.getMatchingTriggers(input);
        List<Object> inputs = new ArrayList<>();
        for (Trigger t : matchingTriggers) {
            inputs.addAll(inputResolver.resolveContainerElements(input, t));
        }
        return inputs;
    }

    // not cached by intention
    @Override
    public Object read(String type, Path path, Charset inputCharset, Object... additionalArguments)
        throws InputReaderException {
        return getInputReader(type).read(path, inputCharset, additionalArguments);
    }

    @Override
    public Object readAll(Path path, Charset inputCharset, Object... additionalArguments) throws InputReaderException {
        Set<String> keySet = PluginRegistry.getTriggerInterpreterKeySet();
        if (keySet != null) {
            for (String s : keySet) {
                try {
                    return getInputReader(s).read(path, inputCharset, additionalArguments);
                } catch (InputReaderException e) {
                    // nothing to do.
                }
            }
        }
        return null;
    }

    /**
     * @param type
     *            of the input
     * @return InputReader for the given type.
     * @throws CobiGenRuntimeException
     *             if no InputReadercould be found
     */
    private InputReader getInputReader(String type) {
        TriggerInterpreter triggerInterpreter = PluginRegistry.getTriggerInterpreter(type);
        if (triggerInterpreter == null) {
            throw new PluginNotAvailableException("TriggerInterpreter", type);
        }
        if (triggerInterpreter.getInputReader() == null) {
            throw new PluginNotAvailableException("InputReader", type);
        }

        return triggerInterpreter.getInputReader();
    }

}
