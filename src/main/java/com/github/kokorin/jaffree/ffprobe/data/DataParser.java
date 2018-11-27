package com.github.kokorin.jaffree.ffprobe.data;

import java.util.*;

public class DataParser {
    // State
    private final Deque<State> stack;

    public DataParser() {
        this.stack = new LinkedList<>();
        this.stack.addLast(new State("ROOT"));
    }

    public void parseLine(String line) {
        if (line.startsWith("[/") && line.endsWith("]")) {
            String name = line.substring(2, line.length() - 1);
            sectionEnd(name);
        } else if (line.startsWith("[") && line.endsWith("]")) {
            String name = line.substring(1, line.length() - 1);
            sectionStart(name);
        } else {
            String[] keyValue = line.split("=");
            if (keyValue.length != 2) {
                throw new RuntimeException("key=value was expected but got: " + line);
            }
            String key = keyValue[0];
            String value = keyValue[1];
            if (!key.contains(":")) {
                property(key, value);
            } else {
                String[] tagKey = key.split(":");
                if (tagKey.length != 2) {
                    throw new RuntimeException("Wrong subsection property format: " + line);
                }

                String tag = tagKey[0];
                key = tagKey[1];

                tagProperty(tag, key, value);
            }
        }
    }

    public void sectionStart(String name) {
        stack.addLast(new State(name));
    }

    public void sectionEnd(String name) {
        if (stack.size() < 2) {
            throw new IllegalStateException("Can't close root section");
        }
        State state = stack.pollLast();

        if (!state.sectionName.equals(name)) {
            throw new RuntimeException("Expecting end of " + state.sectionName + " but found " + name);
        }

        State parent = stack.peekLast();
        parent.subSections.add(state);
    }

    public void property(String key, String value) {
        stack.peekLast().properties.put(key, value);
    }

    public void tagProperty(String name, String key, String value) {
        Map<String, Map<String, String>> tags = stack.peekLast().tags;
        Map<String, String> tag = tags.get(name);
        if (tag == null) {
            tag = new HashMap<>();
            tags.put(name, tag);
        }

        tag.put(key, value);
    }

    public Data getResult() {
        if (stack.size() != 1) {
            throw new IllegalStateException("Parsing failed");
        }

        State root = stack.peek();

        return new Data(toSubSections(root.subSections));
    }

    public static Data parse(Iterator<String> lines) {
        DataParser parser = new DataParser();

        while (lines.hasNext()) {
            String line = lines.next();
            parser.parseLine(line);
        }

        return parser.getResult();
    }

    public static DSection toSection(State state) {
        Map<String, DTag> tags = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : state.tags.entrySet()) {
            tags.put(entry.getKey(), new DTag(entry.getValue()));
        }

        Map<String, List<DSection>> subSections = toSubSections(state.subSections);

        return new DSection(state.properties, tags, subSections);
    }

    public static Map<String, List<DSection>> toSubSections(List<State> subSections) {
        Map<String, List<DSection>> result = new HashMap<>();

        for (State state : subSections) {
            List<DSection> list = result.get(state.sectionName);
            if (list == null) {
                list = new ArrayList<>();
                result.put(state.sectionName, list);
            }

            list.add(toSection(state));
        }

        return result;
    }

    public static class State {
        public final String sectionName;
        public final Map<String, String> properties = new HashMap<>();
        public final Map<String, Map<String, String>> tags = new HashMap<>();
        public final List<State> subSections = new ArrayList<>();

        public State(String sectionName) {
            this.sectionName = sectionName;
        }
    }
}