/* BDict.java: the BObject implementation of a Bencode dictionary */
/* Christopher Chute */

/* Reference: adapted from @frazboyz implementation on BitBucket */
/* https://bitbucket.org/frazboyz/bencoder */

package util.bencode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/* BDict:  BObject for Bencode dictionary */
public class BDict extends HashMap<String, BObject> implements BObject {

    /* encode:  turn BDict into string representation */
    @Override
    public String encode() {
        final StringBuilder buf = new StringBuilder();
        buf.append('d');

        for (final Map.Entry<String, BObject> elt : entrySet()) {
            buf.append(elt.getKey().length()).append(":").append(elt.getKey()).append(elt.getValue().encode());
        }
        buf.append('e');

        return buf.toString();
    }

    /* read:  decodes a BDict from a string */
    public static BDict read(final String str, final AtomicInteger pos) {
        final BDict dict = new BDict();
        if (str.charAt(pos.get()) == 'd') {
            pos.getAndIncrement();
        }

        while (str.charAt(pos.get()) != 'e') {
            final String key = BString.read(str, pos).getString();
            final BObject value = BDecoder.read(str, pos);
            dict.put(key, value);
        }
        pos.getAndIncrement();

        return dict;
    }

    /* print:  produce a human-readable string */
    @Override
    public String print() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Dictionary:\n");
        for (final Map.Entry<String, BObject> elt : entrySet()) {
            buf.append(elt.getKey()).append(" -> ").append(elt.getValue().print()).append("\n");
        }

        return buf.toString();
    }

    @Override
    public BObjectType getType() {
        return BObjectType.BDICT;
    }
}
