/*
Authors (group members): Jude-Anne Forsythe, Jules Siegelwax, Justyn Sinanan, Ethan Smith
Email addresses of group members: jforsythe2024@my.fit.edu, asiegelwax2025@my.fit.edu, jsinanan2024@my.fit.edu, ethan2024@my.fit.edu
Group name: Sentinels
Course: CSE2010
Section: E1
Description of the overall algorithm: We preprocess the dictionary into a byte array and find the optimal opening letter for each word length. During the actual gameplay, we use a "shrinking" list of candidate words that still match up with the revealed pattern and guessed letters. Each guess is gonna be the letter appearing the most in the remaining candidate words, with ties broken by a letter frequency list from DataGenetics. After each feedback call, the candidates are updated (in-place to preserve space).
                                    Version 2: we force garbage collection at three points during preprocessing to minimize the space usage measured by EvalHangmanPlayer. (Idea from GeeksforGeeks https://www.geeksforgeeks.org/java/garbage-collection-in-java/)

*/

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class HangmanPlayer {
    // longest word is 25 characters, but we just want to pad a little
    private static final int MAX_LEN = 30;

    // frequency list of english dictionary letters borrowed from
    // http://www.datagenetics.com/blog/april12012/index.html
    // used to tie-break guesses
    private static final char[] FREQ_ORDER = { 'e', 's', 'i', 'a', 'r', 'n', 't', 'o', 'l', 'c', 'd', 'u', 'p', 'm',
            'g', 'h', 'b', 'y', 'f', 'v', 'k', 'w', 'z', 'x', 'q', 'j' };

    private byte[] wordData; // big byte array of all the dictionary words smushed together
    private int[] wordOffset; // keeps track of where each word starts and stops in wordData

    private int[][] indicesByLength; // sorts each dictionary word by length (so indicesByLength[5] is an array of
                                     // where all the 5 letter words are in wordData)
    private char[] firstGuess; // according to the datagenetics article, the best first guess depends on the
                               // amount of letters a word has

    private int[] candidates; // during guessing, which words are still valid possibilities?
    private int candCount; // arrays are immutable so this tells us how many candidates are still viable
                           // instead of using .length

    private boolean[] guessed; // keeps track of which letters have been guessed
    private boolean firstGuessOfWord; // true only on the first guess, then we will use the best first guess
    private int[] letterCounts = new int[26]; // used to count letters during guess()
    private boolean[] seen = new boolean[26]; // we only count letters once per word to find frequency, so this keeps
                                              // track of letters we have counted already

    // initialize HangmanPlayer with a file of English words
    public HangmanPlayer(String wordFile) throws IOException {
        int[] countPerLen = new int[MAX_LEN + 1]; // number of words of each length
        int totalWords = 0; // to allocate wordOffset size properly
        int totalBytes = 0; // to allocate wordData size properly

        // count up number of words and chars (bytes)
        BufferedReader br1 = new BufferedReader(new FileReader(wordFile));
        String line;
        while ((line = br1.readLine()) != null) {
            int len = cleanLength(line);
            if (len >= 2 && len <= MAX_LEN) {
                countPerLen[len]++;
                totalWords++;
                totalBytes += len;
            }
        }

        br1.close();
        br1 = null; // GC will remove data/objects as long as they are set to null
        line = null;

        // tell GC to get all the garbage from 1st words.txt pass
        System.gc();

        // allocate array lengths
        wordData = new byte[totalBytes];
        wordOffset = new int[totalWords + 1];
        indicesByLength = new int[MAX_LEN + 1][];
        for (int L = 0; L <= MAX_LEN; L++) {
            indicesByLength[L] = new int[countPerLen[L]];
        }

        // fill arrays:
        int[] lenCursor = new int[MAX_LEN + 1]; // keeps track of last-added word in indicesByLength
        int writeByte = 0; // keeps track of last-added word in wordData
        int writeWord = 0; // keeps track of last-added word in wordOffset

        BufferedReader br2 = new BufferedReader(new FileReader(wordFile));
        String line2;
        while ((line2 = br2.readLine()) != null) {
            int len = fillBytes(line2, wordData, writeByte);

            if (len < 0)
                continue; // non-letters or bad length

            wordOffset[writeWord] = writeByte;
            indicesByLength[len][lenCursor[len]++] = writeWord;
            writeByte += len;
            writeWord++;
        }

        br2.close();
        br2.close();
        br2 = null;
        line2 = null;
        lenCursor = null; //both
        countPerLen = null; // not reused

        // now collect garbage from second pass
        System.gc();

        wordOffset[writeWord] = writeByte; // end of last word

        // compute first guesses by counting highest freq letters for each word length
        firstGuess = new char[MAX_LEN + 1];
        boolean[] seenLocal = new boolean[26]; // different from seen[]

        for (int L = 0; L <= MAX_LEN; L++) {
            int[] idxs = indicesByLength[L];
            if (idxs == null || idxs.length == 0)
                continue;
            int[] cts = new int[26];
            for (int wi : idxs) {
                int start = wordOffset[wi];
                for (int i = 0; i < 26; i++)
                    seenLocal[i] = false; // reset seen tracker
                for (int i = 0; i < L; i++) {
                    int b = wordData[start + i] - 'a'; // since they get turned into ASCII
                    if (!seenLocal[b]) {
                        seenLocal[b] = true;
                        cts[b]++;
                    }
                }
            }
            // pick highest count and use FREQ_ORDER to break ties
            char best = 0;
            int bestCt = -1;
            for (char c : FREQ_ORDER) {
                int b = c - 'a';
                if (cts[b] > bestCt) {
                    bestCt = cts[b];
                    best = c;
                }
            }
            firstGuess[L] = best;
            cts = null;
        }
        seenLocal = null; // this also doesn't get reused

        //make sure candididates are held by the largest bucket size since you cant change array sizes
        guessed = new boolean[26];
        seen = new boolean[26];
        int maxBucket = 0;
        for (int i = 0; i <= MAX_LEN; i++) {
                maxBucket = Math.max(maxBucket, indicesByLength[i].length);
        }
        candidates = new int[Math.max(maxBucket, 1)];
        candCount = 0;
        firstGuessOfWord = false;

        // once more at the very end of preprocessing
        System.gc();

    }

    // Measures what word length would be and checks if word is valid. Returns -1 if
    // not valid, and length if valid
    // used for creating wordData and wordOffset lists
    private static int cleanLength(String line) {
        int len = 0; // keeps track of length of word
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // if c is whitespace continue, and if it is not a letter return -1
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (!Character.isAlphabetic(c)) {
                return -1;
            }
            len++;
        }
        return len;
    }

    // Actually finds word length and makes sure all chars are lowercase, returns
    // int length of word
    private static int fillBytes(String line, byte[] wordData, int writeByte) {
        int k = 0; // holds length of a word
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // if c is whitespace continue, if it is uppercase change to lower case, and if
            // it is not a letter return -1
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (Character.isUpperCase(c)) {
                c = Character.toLowerCase(c);
            }
            if (!Character.isAlphabetic(c)) {
                return -1;
            }
            // return -1 if length is greater than max length allowed for a word
            if (k >= MAX_LEN) {
                return -1;
            }
            // adds character converted to byte into wordData at the next empty spot
            wordData[writeByte + k] = (byte) c;
            k++;
        }
        // if length of word is smaller than 2, return -1
        if (k < 2) {
            return -1;
        }
        // returns length of word
        return k;
    }




    // based on the current (partial or intitially blank) word
    // guess a letter
    // currentWord: current word, currenWord.length has the length of the hidden word
    // isNewWord: indicates a new hidden word
    // returns the guessed letter
    // assume all letters are in lower case

    public char guess(String currentWord, boolean isNewWord) {
        // Store the length of the current word pattern.
        int L = currentWord.length();

        // Reset game data if this is the first guess of a new word.
        if (isNewWord) {
            // Mark all letters as not guessed.
            for (int i = 0; i < 26; i++) {
                guessed[i] = false;
            }

            // Load all possible words with the same length.
            if (L >= 0 && L <= MAX_LEN) {
                int[] source = indicesByLength[L];
                candCount = source.length;

                // Copy those words into the candidate list.
                System.arraycopy(source, 0, candidates, 0, candCount);
            } else {
                // Use no candidates if the length is invalid.
                candCount = 0;
            }

            // Mark that the next guess is the first guess for this word.
            firstGuessOfWord = true;
        }

        // Use the precomputed first guess if this is the first turn.
        if (firstGuessOfWord && L >= 0 && L <= MAX_LEN && firstGuess[L] != 0) {
            char best = firstGuess[L];

            // Return the first guess if it has not been used yet.
            if (!guessed[best - 'a']) {
                guessed[best - 'a'] = true;
                firstGuessOfWord = false;
                return best;
            }
        }

        // Set false first-guess mode after the opening turn.
        firstGuessOfWord = false;

        // Clear old letter counts before counting again.
        for (int i = 0; i < 26; i++) {
            letterCounts[i] = 0;
        }

        // Count how many candidate words contain each letter.
        for (int w = 0; w < candCount; w++) {
            int wordIdx = candidates[w];
            int start = wordOffset[wordIdx];
            int end = wordOffset[wordIdx + 1];

            // Reset seen letters for this candidate word.
            for (int i = 0; i < 26; i++) {
                seen[i] = false;
            }

            // Scan each letter in the current candidate word.
            for (int i = start; i < end; i++) {
                byte letter = wordData[i];
                int b = letter - 'a';

                // Count each letter only once per word.
                if (!seen[b]) {
                    seen[b] = true;
                    letterCounts[b]++;
                }
            }
        }

        // Track the best letter to guess next.
        char best = 0;
        int bestCount = -1;

        // Pick the best unused letter using frequency-order tie breaking.
        for (int i = 0; i < FREQ_ORDER.length; i++) {
            char c = FREQ_ORDER[i];
            int b = c - 'a';

            // Skip letters that were already guessed.
            if (guessed[b]) {
                continue;
            }

            // Update the best guess if this letter appears in more words.
            if (letterCounts[b] > bestCount) {
                bestCount = letterCounts[b];
                best = c;
            }
        }

        // Fall back to the next unused common letter if needed.
        if (best == 0 || bestCount <= 0) {
            for (int i = 0; i < FREQ_ORDER.length; i++) {
                char c = FREQ_ORDER[i];

                // Pick the first unused fallback letter.
                if (!guessed[c - 'a']) {
                    best = c;
                    break;
                }
            }
        }

        // Mark the chosen letter as guessed and return it.
        guessed[best - 'a'] = true;
        return best;
    }

    // feedback on the guessed letter
    // isCorrectGuess: true if the guessed letter is one of the letters in the hidden word
    // currentWord: partially filled or blank word
    //
    // Case isCorrectGuess currentWord
    // a. true partial word with the guessed letter
    // or the whole word if the guessed letter was the
    // last letter needed
    // b. false partial word without the guessed letter

    public void feedback(boolean isCorrectGuess, String currentWord) {
        int L = currentWord.length();
        int write = 0; // where to put the next candidate

        for (int read = 0; read < candCount; read++) {
            int wordIdx = candidates[read];
            int start = wordOffset[wordIdx];
            int end = wordOffset[wordIdx + 1];

            // Defensive length check — drop words that don't match board length
            if ((end - start) != L) {
                continue;
            }

            // Keep only candidates still consistent with the current board
            if (consistent(start, currentWord, L)) {
                candidates[write] = wordIdx;
                write++;
            }
            // else: drop it and don't carry it forward
        }

        candCount = write; // survivors only
    }

    // Helper: is this candidate word still possible as the hidden word?
    private boolean consistent(int start, String currentWord, int L) {
        for (int i = 0; i < L; i++) {
            byte candidateLetter = wordData[start + i];
            char patternChar = currentWord.charAt(i);

            if (patternChar != ' ') {
                // Revealed spot: candidate letter must match exactly
                if (candidateLetter != (byte) patternChar) {
                    return false;
                }
            } else {
                // Blank spot: candidate's letter here must NOT be one we've already guessed
                // (if it were in the hidden word, the evaluator would have revealed it)
                if (guessed[candidateLetter - 'a']) {
                    return false;
                }
            }
        }
        return true;
    }
}
