package com.pocketdex.launcher.eco;

public final class ExpEval {
    private ExpEval() {
    }

    public static double evaluate(String source) throws Exception {
        Parser parser = new Parser(source);
        double value = parser.parseExpression();
        parser.skip();
        if (parser.pos != parser.src.length()) {
            throw new Exception("unexpected character: " + parser.src.charAt(parser.pos));
        }
        return value;
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        private double parseExpression() throws Exception {
            double value = parseTerm();
            while (true) {
                char op = peek();
                if (op == '+') {
                    pos++;
                    value += parseTerm();
                } else if (op == '-') {
                    pos++;
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() throws Exception {
            double value = parsePower();
            while (true) {
                char op = peek();
                if (op == '*') {
                    pos++;
                    value *= parsePower();
                } else if (op == '/') {
                    pos++;
                    double divisor = parsePower();
                    if (divisor == 0d) {
                        throw new ArithmeticException("divide by zero");
                    }
                    value /= divisor;
                } else if (op == '%') {
                    pos++;
                    double operand = parsePower();
                    if (operand == 0d) {
                        throw new ArithmeticException("modulo zero");
                    }
                    value %= operand;
                } else {
                    return value;
                }
            }
        }

        private double parsePower() throws Exception {
            double base = parseUnary();
            if (peek() == '^') {
                pos++;
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() throws Exception {
            if (peek() == '-') {
                pos++;
                return -parseUnary();
            }
            return parseAtom();
        }

        private double parseAtom() throws Exception {
            skip();
            if (pos < src.length() && src.charAt(pos) == '(') {
                pos++;
                double value = parseExpression();
                skip();
                if (pos >= src.length() || src.charAt(pos) != ')') {
                    throw new Exception("expected )");
                }
                pos++;
                return value;
            }
            int start = pos;
            while (pos < src.length()
                    && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new Exception("number expected");
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private char peek() {
            skip();
            char op = ' ';
            if (pos < src.length()) {
                op = src.charAt(pos);
                if ("+-*/%".indexOf(op) < 0) {
                    op = ' ';
                }
            }
            return op;
        }

        private void skip() {
            while (pos < src.length()
                    && (src.charAt(pos) == ' ' || src.charAt(pos) == '\t')) {
                pos++;
            }
        }
    }
}
