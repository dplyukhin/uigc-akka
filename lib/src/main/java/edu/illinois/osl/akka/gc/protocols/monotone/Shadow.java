package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.HashMap;

class Shadow {
    /** A list of unreleased refobs pointing to this actor. */
    HashMap<Token, Integer> incoming;
    /** A list of unreleased refobs pointing from this actor. */
    HashMap<RefLike<?>, Shadow> outgoing;
    /** A shadow is marked if it is potentially unblocked in the current history */
    boolean isMarked;

    public Shadow() {
        incoming = new HashMap<>();
        outgoing = new HashMap<>();
        isMarked = false;
    }
}
