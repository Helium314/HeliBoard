package org.oscar.kb.AIEngine;

    public class AIOutputEvent {
        private final String aiOutput;


        public AIOutputEvent(String aiOutput) {
            this.aiOutput = aiOutput;
        }

        public String getAiOutput() {
            return aiOutput;
        }

}
