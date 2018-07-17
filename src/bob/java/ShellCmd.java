package bob.java;

/*
 * Copyright (c) 2001-2010, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initiative Approved is a trademark of the Open Source Initiative.
 *
 * This file is part of DrJava.  Download the current version of this project
 * from http://www.drjava.org/ or http://sourceforge.net/projects/drjava/
 */

// TODO: Try writing in clojure

import java.util.List;
import java.util.LinkedList;


public class ShellCmd {
    private static final int NO_TOKEN_STATE = 0;
    private static final int NORMAL_TOKEN_STATE = 1;
    private static final int SINGLE_QUOTE_STATE = 2;
    private static final int DOUBLE_QUOTE_STATE = 3;

    /**
     * Tokenizes the given String into String tokens.
     *
     * @param arguments A String containing one or more command-line style arguments to be tokenized.
     * @param stringify whether or not to include escape special characters
     * @return A list of parsed and properly escaped arguments.
     */
    public static List<String> tokenize(String arguments) {
        LinkedList<String> argList = new LinkedList<>();
        StringBuilder currArg = new StringBuilder();
        boolean escaped = false;
        int state = NO_TOKEN_STATE;  // start in the NO_TOKEN_STATE
        int len = arguments.length();

        // Loop over each character in the string
        for (int i = 0; i < len; i++) {
            char c = arguments.charAt(i);

            if (escaped) {
                // Escaped state: just append the next character to the current arg.
                escaped = false;
                currArg.append(c);
            } else {
                switch (state) {
                    case SINGLE_QUOTE_STATE:
                        if (c == '\'') {
                            // Seen the close quote; continue this arg until whitespace is seen
                            state = NORMAL_TOKEN_STATE;
                        } else {
                            currArg.append(c);
                        }
                        break;
                    case DOUBLE_QUOTE_STATE:
                        if (c == '"') {
                            // Seen the close quote; continue this arg until whitespace is seen
                            state = NORMAL_TOKEN_STATE;
                        } else if (c == '\\') {
                            // Look ahead, and only escape quotes or backslashes
                            i++;
                            char next = arguments.charAt(i);
                            if (next == '"' || next == '\\') {
                                currArg.append(next);
                            } else {
                                currArg.append(c);
                                currArg.append(next);
                            }
                        } else {
                            currArg.append(c);
                        }
                        break;
                    case NO_TOKEN_STATE:
                    case NORMAL_TOKEN_STATE:
                        switch (c) {
                            case '\\':
                                escaped = true;
                                state = NORMAL_TOKEN_STATE;
                                break;
                            case '\'':
                                state = SINGLE_QUOTE_STATE;
                                break;
                            case '"':
                                state = DOUBLE_QUOTE_STATE;
                                break;
                            default:
                                if (!Character.isWhitespace(c)) {
                                    currArg.append(c);
                                    state = NORMAL_TOKEN_STATE;
                                } else if (state == NORMAL_TOKEN_STATE) {
                                    // Whitespace ends the token; start a new one
                                    argList.add(currArg.toString());
                                    currArg = new StringBuilder();
                                    state = NO_TOKEN_STATE;
                                }
                        }
                        break;
                    default:
                        throw new IllegalStateException("ArgumentTokenizer state " + state + " is invalid!");
                }
            }
        }

        // If we're still escaped, put in the backslash
        if (escaped) {
            currArg.append('\\');
            argList.add(currArg.toString());
        } else if (state != NO_TOKEN_STATE) {
            // Close the last argument if we haven't yet
            argList.add(currArg.toString());
        }

        return argList;
    }
}
