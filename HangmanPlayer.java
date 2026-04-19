/*

  Authors (group members):
  Email addresses of group members:
  Group name:

  Course:
  Section:

  Description of the overall algorithm:


*/


public class HangmanPlayer
{

    // initialize HangmanPlayer with a file of English words
    public HangmanPlayer(String wordFile)
    {

    }

    // based on the current (partial or intitially blank) word
    //    guess a letter
    // currentWord: current word, currenWord.length has the length of the hidden word
    // isNewWord: indicates a new hidden word
    // returns the guessed letter
    // assume all letters are in lower case
    public char guess(String currentWord, boolean isNewWord)
    {
	char guess = ' ';
	
        return guess;
    }

    // feedback on the guessed letter
    // isCorrectGuess: true if the guessed letter is one of the letters in the hidden word
    // currentWord: partially filled or blank word
    //   
    // Case       isCorrectGuess      currentWord   
    // a.         true                partial word with the guessed letter
    //                                   or the whole word if the guessed letter was the
    //                                   last letter needed
    // b.         false               partial word without the guessed letter
    public void feedback(boolean isCorrectGuess, String currentWord)
    {
		int L = currentWord.length();
    int write = 0; // where to put the next candidate

    for (int read = 0; read < candCount; read++) {
        int wordIdx = candidates[read];
        int start = wordOffset[wordIdx];
        int end   = wordOffset[wordIdx + 1];

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
private boolean consistent(int start, String currentWord, int L)
{
    for (int i = 0; i < L; i++) {
        byte  candidateLetter = wordData[start + i];
        char  patternChar     = currentWord.charAt(i);

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
