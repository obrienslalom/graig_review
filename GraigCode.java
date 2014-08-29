package com.example.graig2;

import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.FloatMath;
import android.util.Log;

enum GameState
{
	Main_Menu,
	Bid_Start,
	Bid_Finish,
	Redraw,
	Play,
	Play_End,
	Points	
}

public class MyRenderer implements GLSurfaceView.Renderer
{
	private Context m_Context;
	private Camera m_Camera;
	private PointLight m_PointLight;
	
	//Pitch Variables
	//Set up the game with fixed players, teams, win condition for now
	private int numPlayers = 4;
	private int numTeams = 2;
	private int victory = 15;
	private int dealerPosition = 1;	//indicates which player[] is the dealer
	private DeckofCards dealer;		//creates a deckofcards named dealer
	private Team[] teams;
	private Player[] players;
	/*Deprecated from non-3D code
	//private int winningTeam = -1;
	//private int round = 0;
	//private Cube m_Cube;
	//private boolean isWinner = false;
	*/
	
	
	//Pitch Environmental Variables
	//Follow-up: Menu/Board are basically the same thing with different layouts, think I should leverage the same class for these
	private Menu m_Menu;	//Menu used to display
	private Board m_Board;  //Game Board - used to display bid choices and cards played
	private GameState m_GameState = GameState.Main_Menu;  //tracks the phases of the game, which is used to determine expected I/O
		
	
	//Touch variables
	float Startx;
	float Starty;
	float Endx;
	float Endy;
	float Deltax;
	float Deltay;
	boolean ScreenTouched = false;
	boolean ScreenDrag = false;
	
	
	//Bid variables
	private boolean PlayerBid = false;
	private int BidPhase = 1;
	private int bidder = -1;
	private int trumpSuit;
	private int[] CurrentBid = {0, 0, 0, 0};  
	/*Follow-up: There must be a better way to manage this
	//CurrentBid[0] represents the number of points that the bidder has bid
	//CurrentBid[1] represents the player that won the bid
	//CurrentBid[2] represents the team that won the bid
	//CurrentBid[3] represents whether there was a misdeal
	//--------- CurrentBid[3] == -1 when the dealer forfeits
	//--------- CurrentBid[3] == 1 when there is a misdeal
	//--------- CurrentBid[3] == 0 if there was a successful deal/bid
	*/
	
	
	//Controls where cards are placed
	float x = 0;
	float y = 0;
	float z = -4.0f;
	
	
	//Display and Light variables
	//Follow-up: Copied the light variables from book, may need to rework them when changing from rotating pointlight to spotlight 
	//or just remove and use ambient
	private int ViewPortWidth;
    private int ViewPortHeight; 
	public float[] mLightModelMatrix = new float[16];	//Stores a copy of the model matrix specifically for the light position.
	/** Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
	 *  we multiply this by our transformation matrices. */
	public final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	/** Used to hold the current position of the light in world space (after transformation via model matrix). */
	public final float[] mLightPosInWorldSpace = new float[4];
	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	public final float[] mLightPosInEyeSpace = new float[4];

	
	public MyRenderer(Context context)
	{
		//Follow-up: Don't fully understand the context variable, look into this more
		m_Context = context;
	}
	
	void CreateLight(Context iContext)
	{
		Shader PointLightShader = new Shader(iContext, R.raw.vspointlightsource, R.raw.fspointlightsource, new String[] {"a_Position"} );
		m_PointLight = new PointLight(iContext, PointLightShader);
	}
	
	void SetupCamera()
	{
		// Create a new perspective projection matrix. The height will stay the same
		// while the width will vary as per aspect ratio.
		float ratio = (float) ViewPortWidth / ViewPortHeight;
		final float Projleft = -ratio;
		final float Projright = ratio;
		
		m_Camera = new Camera(m_Context, Projleft, Projright, ViewPortWidth);
	}
	
	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) 
	{
		// Set the background clear color to blackish.
		GLES20.glClearColor(0.2f, 0.2f, 0.2f, 0.0f);

		// Use culling to remove back faces.
		//GLES20.glEnable(GLES20.GL_CULL_FACE);

		// Enable depth testing so that objects behind other objects aren't rendered
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);

		m_Menu = new Menu(m_Context);
		InitializeGame();
		
		//Need to change light to a spotlight "above table"
		CreateLight(m_Context);
				
	}

	public void InitializeGame()
	{
		//Create and shuffle deck
		dealer = new DeckofCards(m_Context);
		dealer.shuffleDeck();
		
		//Create the board which holds the bid and suit options
		m_Board = new Board(m_Context);
		
		//Create teams and players
		teams = new Team[numTeams];
		players = new Player[numPlayers];
		
		//assigns player name and team to the players[]
		for(int p = 0; p < numPlayers; p++)
		{
		   players[p] = new Player("Player " + (p+1), p%numTeams);
		}

		for(int t = 0; t < numTeams; t++)
		{
		   teams[t] = new Team("Team " + (t+1), t);
		}
		
		//Assigns dealer
		Random r = new Random();
		dealerPosition = r.nextInt(numPlayers);
	}
	
	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) 
	{
		// Set the OpenGL viewport to the same size as the surface.
		GLES20.glViewport(0, 0, width, height);
		ViewPortWidth = width;
        ViewPortHeight = height;
		
		SetupCamera(); //uses ViewPort variables above
		m_Board.updateBoard(m_Camera, m_PointLight);
	}
	
	void ProcessTouch(float _Startx, float _Starty, float _Endx, float _Endy)
	{
		Deltax = _Startx - _Endx;
		Deltay = _Starty - _Endy;
		
		float length = FloatMath.sqrt((Deltax * Deltax) + (Deltay * Deltay));
		
		//The user did not intend to move from starting press point
		if(length < 10)
		{
			Startx = _Startx;
			Starty = _Starty;
			Endx = _Startx;
			Endy = _Starty;
		}
		else
		{
			Startx = _Startx;
			Starty = _Starty;
			Endx = _Endx;
			Endy = _Endy;
		}
		
		ScreenTouched = true;
	}
	
	
	void ProcessDrag(float Deltax, float Deltay)
	{
		
	}

	//Determines the input from the user based on the current game state
	void CheckTouch()
	{
		if(m_GameState == GameState.Main_Menu)
		{
			//New Game button is pressed
			if(m_Menu.Buttons[0].Touched(Startx, Starty, ViewPortWidth, ViewPortHeight))
			{
				m_GameState = GameState.Bid_Start; //Advance Game State
				dealer.shuffleDeck();
				initialDeal();
			}
		}
		
		/*Follow-up: I should rework the bid.. to start have the result call a bid method.  Secondly, look for a better option than an int[]
		Also need to add in some error messaging if they try to bid lower than allowed */
		else if(m_GameState == GameState.Bid_Start)
		{
			//Determines what, if any, button was pressed for the bid
			BidSelections result = m_Board.GetPlayerBid(Startx, Starty, ViewPortWidth, ViewPortHeight);
			
			if (result == BidSelections.Pass)
			{
				PlayerBid = true;
			}
			else if (result == BidSelections.Two)
			{
				//Checks that another player hasn't bid higher
				if( CurrentBid[0] < 2)
				{
					CurrentBid[0] = 2;  //Sets current bid to 2
					CurrentBid[1] = 0;  //Sets current bid owner to the player
					CurrentBid[2] = 0;  //Sets the current bid team to the player's team
					PlayerBid = true;   //Indicates that the player has bid
				}
			}
			else if (result == BidSelections.Three)
			{
				//Checks that another player hasn't bid higher
				if( CurrentBid[0] < 3)
				{
					CurrentBid[0] = 3;  //Sets current bid to 2
					CurrentBid[1] = 0;  //Sets current bid owner to the player
					CurrentBid[2] = 0;  //Sets the current bid team to the player's team
					PlayerBid = true;   //Indicates that the player has bid
				}
			}
			else if( result == BidSelections.Four)
			{
				//Checks that another player hasn't bid higher
				//Follow-up: Need to check for circumstance where they are dealer and allowed to overbid
				if( CurrentBid[0] < 4)
				{
					CurrentBid[0] = 4;  //Sets current bid to 2
					CurrentBid[1] = 0;  //Sets current bid owner to the player
					CurrentBid[2] = 0;  //Sets the current bid team to the player's team
					PlayerBid = true;   //Indicates that the player has bid
				}
			}
		}
		else if(m_GameState == GameState.Bid_Finish)
		{
			//Determines what, if any, button was pressed for the suit
			SuitSelections result = m_Board.GetBidSuit(Startx, Starty, ViewPortWidth, ViewPortHeight);
			
			//If the player won the bid
			if(CurrentBid[1] == 0)
			{
				if(result == SuitSelections.Hearts)
				{
					CurrentBid[3] = 0;  //The bid wasn't meant to hold trump suit.. this is placeholder
					m_GameState = GameState.Redraw;
				}
				else if(result == SuitSelections.Diamonds)
				{
					CurrentBid[3] = 1;  //The bid wasn't meant to hold trump suit.. this is placeholder
					m_GameState = GameState.Redraw;
				}
				else if(result == SuitSelections.Clubs)
				{
					CurrentBid[3] = 2;  //The bid wasn't meant to hold trump suit.. this is placeholder
					m_GameState = GameState.Redraw;
				}
				else if(result == SuitSelections.Spades)
				{
					CurrentBid[3] = 3;  //The bid wasn't meant to hold trump suit.. this is placeholder
					m_GameState = GameState.Redraw;
				}
			}
			else
			{
				//If the player didn't win the bid they need to touch screen to continue
				//Follow-up: Add a message indicated this
				if( ScreenTouched)
					m_GameState = GameState.Redraw;
			}
		}
		else if (m_GameState == GameState.Redraw)
		{
			//Determines what, if any, card was pressed to redraw/
			int result = players[0].getTouchedCard(Startx, Starty, ViewPortWidth, ViewPortHeight);
			
			if( result == -1)
			{
				Log.e("GPS", "Game State = Play");
				m_GameState = GameState.Play;
			}
			else
			{
				//If the player touches the card for the first time it is flagged to be t hrown away
				//CArd will be drawn higher on the Y axis if throwAway == true
				if(players[0].hand.get(result).throwAway == false)
					players[0].hand.get(result).throwAway = true;
				else if(players[0].hand.get(result).throwAway)  //Player changed their mind and is no longer flagged to be thrown away
					players[0].hand.get(result).throwAway = false;
			}
		}
	}
	
	//Determines the output based on the current game state
	void RenderScene()
	{
		if(m_GameState == GameState.Main_Menu)
		{
			m_Menu.drawMenu(m_Camera, m_PointLight);
		}
		else if(m_GameState == GameState.Bid_Start)
		{
			//draws the player's hand
			players[0].showHand(m_Camera, m_PointLight);
			
			//BidPhase == 1 when the player has not bid yet
			if(BidPhase == 1)
			{
				bidder = dealerPosition + 1;
				
				while(bidder < numPlayers)
				{
					//CurrentBid = ComputerBid();  /* A.I. bid here */
					bidder++;
				}
				
				BidPhase++;
			}
			
			//Board sets up the bid buttons and computer dialogue
			m_Board.drawBoard(m_Camera, m_PointLight, m_GameState, CurrentBid);

			//BidPhase == 2 when it's the player's turn to bid
			if(BidPhase == 2)
			{
				//Waits for player's bid input then processes remaining bidders
				if(PlayerBid)
				{
					bidder = 1; //Sets bidder to the first computer
					while(bidder <= dealerPosition)
					{
						//CurrentBid = ComputerBid();  /* A.I. bid here */
						bidder++;
					}
					
					m_GameState = GameState.Bid_Finish;
				}
			}
		}
		else if(m_GameState == GameState.Bid_Finish)
		{
			players[0].showHand(m_Camera, m_PointLight);
			// Draws the board with Select Suit if player wins bid (determined from CurrentBid)
			m_Board.drawBoard(m_Camera, m_PointLight, m_GameState, CurrentBid);
		}
		else if(m_GameState == GameState.Redraw)
		{
			players[0].showHand(m_Camera, m_PointLight);
			m_Board.drawBoard(m_Camera, m_PointLight, m_GameState, CurrentBid);
		}
		else if(m_GameState == GameState.Play)
		{
			reDraw(); //Discards all cards with throwAway == true and fills the players hand
			if(ScreenDrag != true)
				players[0].showHand(m_Camera, m_PointLight);
			/*
			 * Need to add in player selecting and moving card to play
			 */
		}
	}
	
	@Override
	public void onDrawFrame(GL10 glUnused) 
	{
		GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);			        
		
		if(ScreenTouched)
		{
			CheckTouch();
			ScreenTouched = false;
		}
		
		RenderScene();
		
		/*Code to be replaced/moved
		if(isWinner())
		{
			
		}
		else
		{
			dealerPosition += playRound();
			DisplayScoreBoard();
			CleanUp();
		}
        */
		// Do a complete rotation every 10 seconds.
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);
         
        m_PointLight.updateLight(m_Camera, angleInDegrees);
        	
        //dealer.deck[10].drawCard(m_Camera, m_PointLight, 0, x, y, z);
        	
        //m_PointLight.drawLight(m_Camera, angleInDegrees);        
	}
	
	//resets the deck and the tricks owned by the teams
    //Should we also clear the players hands here even though they should be empty?
    public void CleanUp()
    {
    	dealer.reset();

    	for( int p = 0; p < players.length; p++)
    		players[p].hand.clear();

		for ( int t = 0; t < teams.length; t++ )
			teams[t].newRound();
    }
	   
	public void DisplayScoreBoard()
    {
		for( int t = 0; t < teams.length; t++)
			System.out.println(teams[t].teamName + " has " + teams[t].score + " points.");
    }
	
	//Deals each player a hand assuming they do not already have one
	public void initialDeal()
	{
		int dealTo = dealerPosition + 1;

		//represents 2 phases of dealing (3 cards at a time)
		for(int i = 0; i <= 3; i+=3) //runs twice
		{
			for(int p = 0; p < players.length; p++) //runs for each player
			{
				if(dealTo >= players.length)
					dealTo -= players.length;

				for(int c = 0; c < 3; c++) //runs 3 times, dealing 1 card per run
				{
					players[dealTo].addCard(dealer.dealCard(dealTo));
				}

				dealTo++; //adjusts the player that is dealt to
			}
		}
	}
	
	public void reDraw( )
    {
		for(int p = 0; p < numPlayers; p++)
		{
			for(int c = players[p].hand.size() - 1; c >= 0; c--)
			{
				if(players[p].hand.get(c).throwAway)
				{
					players[p].hand.remove(c);
					players[p].addCard(dealer.dealCard(p));
				}
			}
		}
    }
	
/*******************************************************************************
 * All Code below this was written for non-graphics game that I developed prior to doing graphics, so needs to be revisited but all non
   Input/Output should stay pretty much the same
	
	public void determinePoints ( int[] theBid, int trumpSuit )
	{
		int[] points = new int[teams.length];
		int mostGame = 0;
		int curHigh = 1;
		int curLow = 14;
		int highWinner = -1;
		int lowWinner = -1;
		int jackWinner = -1;
		int gameWinner = -1;
		boolean gameTie = true;
		
		for( int j = 0; j < teams.length; j++)
		{
		   if(teams[j].hasJack(trumpSuit))
			jackWinner = j;
		}

		for( int g = 0; g < teams.length; g++)
		{
		   if(teams[g].getGamePoints() > mostGame)
		   {
			gameWinner = g;
			gameTie = false;
			mostGame = teams[g].getGamePoints();
		   }
		   else if(teams[g].getGamePoints() == mostGame)
			gameTie = true;	   
		}

		for( int h = 0; h < teams.length; h++)
		{
		   if(teams[h].getHighCard(trumpSuit).rank > curHigh)
		   {
			highWinner = h;
			curHigh = teams[h].getHighCard(trumpSuit).rank;
		   }
		}

		for( int l = 0; l < teams.length; l++)
		{
		   if(teams[l].getLowCard(trumpSuit).rank < curLow)
		   {
			lowWinner = l;
			curLow = teams[l].getLowCard(trumpSuit).rank;
		   }
		}
		
		//Awards jack point
		if(jackWinner != -1)
		   points[jackWinner]++;

		//Awards game point
		if(gameTie == false || gameWinner != -1)
		   points[gameWinner]++;

		//Awards high point
		if(highWinner != -1)
		   points[highWinner]++;

		//Awards low point
		if(lowWinner != -1)
		   points[lowWinner]++;

		//Determines if the bidder went up
		if(points[theBid[2]] < theBid[0])
		{
		   System.out.println(teams[theBid[2]].teamName + " went up for " + theBid[0]);
		   points[theBid[2]] = 0 - theBid[0];
		}

		for(int p = 0; p < teams.length; p++)
		{
		   teams[p].addPoints(points[p]);
		}

		System.out.println("High = " + teams[highWinner].getHighCard(trumpSuit));
		System.out.println("Low = " + teams[lowWinner].getLowCard(trumpSuit));
		System.out.println("Game total = " + teams[gameWinner].getGamePoints());	
	}
	
	/*
	int playRound()
	{
		if(dealerPosition >= numPlayers)
			dealerPosition -= numPlayers;
		
		System.out.println(players[dealerPosition].name + " is dealer.");
		
		//theBid[0] represents the number of points that the bidder has bid
		//theBid[1] represents the player that won the bid
		//theBid[2] represents the team that won the bid
		//theBid[3] represents whether there was a misdeal
		//--------- theBid[3] == -1 when the dealer forfeits
		//--------- theBid[3] == 1 when there is a misdeal
		//--------- theBid[3] == 0 if there was a successful deal/bid
		
		int[] theBid = {0,0,0,0};
		int trumpSuit = 0;
		
		Scanner scanner = new Scanner(System.in);

		dealer.shuffleDeck();
		initialDeal();
		theBid = Bid();

		if( theBid[3] == -1 )
		{
		   System.out.println( "The dealer forfeit his bid" );
		   teams[players[dealerPosition].team].addPoints( -2 );
		   return 1;
		}
		else if( theBid[3] == 1 )
		{
		   System.out.println( "There was a misdeal" );
		   return 0;
		}
		else  
		{
		   System.out.println(players[theBid[1]].name + " won the bid with a bid of " + theBid[0]);
		   System.out.print(players[theBid[1]].name + " sets trump to: ");
		   trumpSuit = scanner.nextInt();

		   System.out.println("Trump is " + dealer.cardSuit[trumpSuit]);

		   discardPhase( trumpSuit );
		
		   play( trumpSuit, theBid );
		   System.out.println("Team 1 tricks:");
		   teams[0].showTricks();
		   System.out.println("Team 2 tricks:");
		   teams[1].showTricks();
		   determinePoints( theBid, trumpSuit );
		   return 1;
		}
	}
	
	
	
	
	
	public void play( int trumpSuit, int[] theBid )
	{
		int playerTurn = theBid[1];
		int suitLead;
		int lastTrickWinner = playerTurn;
		ArrayList<Card3D> curTrick = new ArrayList<Card3D>(players.length);
		
		for( int t = 0; t < 6; t++)
		{
		   suitLead = -1;

		   for( int i = 0; i < players.length; i++)
		   {
			if(playerTurn >= players.length)
			   playerTurn -= players.length;

		   	curTrick.add(players[playerTurn].playCard(suitLead, trumpSuit));
		   	if(i == 0)
			   suitLead = curTrick.get(i).suitNum;

			System.out.println("The current trick is:");
		   	for(int c = 0; c < curTrick.size(); c++)
			   System.out.println(curTrick.get(c));

		   	playerTurn++;
		   }
		   lastTrickWinner = determineWinner(curTrick, suitLead, trumpSuit);
		   System.out.println(players[lastTrickWinner].name + " won the trick");
		   teams[players[lastTrickWinner].team].tricks.addAll(curTrick);
		   playerTurn = lastTrickWinner;
		   curTrick.clear();
		}
	}
	
	
	public int determineWinner( ArrayList<Card3D> curTrick, int suitLead, int trumpSuit )
	{
		Card3D leaderCard = curTrick.get(0);
		
		for(int c = 1; c < curTrick.size(); c++)
		{
		   if( (curTrick.get(c).suitNum == suitLead) || (curTrick.get(c).suitNum == trumpSuit) )
			leaderCard = (leaderCard.value(trumpSuit) > curTrick.get(c).value(trumpSuit) ? leaderCard : curTrick.get(c));
		}

		return leaderCard.owner;
	}
	
	public void discardPhase( int trumpSuit )
	   {
		Scanner scanner = new Scanner(System.in);
		int toDiscard;
		int dealTo = dealerPosition + 1;

		for( int p = 0; p < players.length; p++)
		{
		   if(dealTo >= players.length)
	                dealTo -= players.length;

		   toDiscard = -1;
		   while(toDiscard != 0)
		   {
			players[dealTo].showHand(m_Camera, m_PointLight);
			System.out.print("Select a card to discard 1 - " + players[dealTo].hand.size() + " (0 to exit): ");
			toDiscard = scanner.nextInt();

			if(toDiscard == 0)
			{}
			else if(toDiscard < 0 || toDiscard > players[dealTo].hand.size())
			{
			   System.out.println("*******************************************************");
			   System.out.println("Invalid input");
			   System.out.println("*******************************************************");
			}
			else if( players[dealTo].hand.get(toDiscard - 1).suitNum == trumpSuit )
			{
			   System.out.println("*******************************************************");
			   System.out.println("Can't discard trump scumbag + " + players[dealTo].hand.get(toDiscard - 1));
			   System.out.println("*******************************************************");
			}
			else
			   players[dealTo].hand.remove(toDiscard - 1);
		   }
		
		   //reDraw( dealTo );
		   players[dealTo].showHand(m_Camera, m_PointLight);
		   dealTo++;
		}
	}
	
	public int[] tenAndUnder( int[] carryOver, int bidder  )
	   {
		//tenUnder[0] determines if the player redraws (1) or not (0)
		//tenUnder[1] determines player that redrew
		//tenUnder[2] determines how many 10 and unders there are
		int[] tenUnder = carryOver;
		Scanner scanner = new Scanner(System.in);

		while( players[bidder].isValidHand() == false && tenUnder[2] < 2 )
	        {
		   players[bidder].showHand(m_Camera, m_PointLight);

	           if ( tenUnder[2] == 1 )
	        	System.out.print("Two 10-and-unders have been dealt, throw in for misdeal? 1 = Yes, 2 = No" );
	           else
	                System.out.print("You have a 10-and-under, would you like to forfeit your bid to redraw a new hand? (1 = Yes, 2 = No): ");

	           tenUnder[0] = scanner.nextInt();
	           if ( tenUnder[0] == 1 && tenUnder[2] < 2 )
	           {
			tenUnder[1] = bidder;
	               	tenUnder[2]++;
	               	if( tenUnder[2] < 2 )
	               	{
	               	    players[bidder].hand.clear();
	               	    //reDraw( bidder );
	               	}
	           }
		   else if ( tenUnder[0] == 2 )
			break;
		   else if (tenUnder[2] >= 2 )
			{}
		   else
			System.out.println("Invalid input");
	        }   

		return tenUnder;
	   }
		
	//Gives each player an opportunity to make a bid
   //ERROR: If a user provides non-integer input the program will crash
   public int[] Bid() 
   {
	//theBid[0] represents the number of points that the bidder has bid
	//theBid[1] represents the player that won the bid
	//theBid[2] represents the team that won the bid
	//theBid[3] represents whether the game will proceed
	//--------- theBid[3] == -1 when the dealer forfeits
	//--------- theBid[3] == 1 when there is a misdeal
	//--------- theBid[3] == 0 if there was a successful deal/bid
	int[] theBid = {0, 0, 0, 0};

	//tenUnder[0] determines if the player redraws (1) or not (0)
	//tenUnder[1] determines who the player is
	//tenUnder[2] determines how many 10 and unders there are
	int[] tenUnder = {0, -1, 0};
	int bid = 0;
	int bidder = dealerPosition + 1;
	int throwIn = 0;

	Scanner scanner = new Scanner(System.in);

	for(int i = 0; i < players.length; i++)
	{
	   if(bidder >= players.length)
		bidder -= players.length;

	   if( players[bidder].isValidHand() == false)
		tenUnder = tenAndUnder(tenUnder, bidder );

	   players[bidder].showHand(m_Camera, m_PointLight);

	   if( tenUnder[2] >= 2 )
	   {
		theBid[3] = 1;	
		continue;
	   }
	   else if( theBid[0] == 0 && bidder == dealerPosition)
           {
                System.out.print("You are stuck with the bid.  Would you like to forfeit the hand and lose 2 points? (1 = Yes, 2 = No): ");
		throwIn = scanner.nextInt();
		
		if ( throwIn == 1 )
           	{
                   theBid[3] = -1;
                   continue;
           	}
           	else if ( throwIn == 2 )
           	{
                   theBid[0] = 2;
                   theBid[1] = bidder;
                   theBid[2] = players[bidder].team;
                   continue;
           	}
	   }
	   else if( tenUnder[1] == bidder)
	   {
		System.out.println("You can't bid because you threw in a 10-and-under");
	   }
	   else
	   {
		System.out.print(players[bidder].name + "'s bid: ");
	   	bid = scanner.nextInt();

	   	if(bid < 2 || bid > 4) //improper bids are treated as passes
	   	{
		   System.out.println(players[bidder].name + " passes.");
	   	}
	   	else if( bid > theBid[0] )
	   	{
		   theBid[0] = bid;
		   theBid[1] = bidder;
		   theBid[2] = players[bidder].team;
	  	}
	   	else if( bid == 4 && bidder == dealerPosition)
	   	{
		   theBid[0] = bid;
		   theBid[1] = bidder;
		   theBid[2] = players[bidder].team;
	   	}
	   	else if(bid <= theBid[0]) //ensures the bid is higher than the current bid, unless it's the dealer's bid
           	{
                   System.out.println("*******************************************************");
		   System.out.println("The bid is already " + theBid[0] + " dumb ass");
		   System.out.println("*******************************************************");
                   bidder--;
                   i--;
           	}
	   }
	   bidder++;
	}

	return theBid;
   }
	   
   
	   
	boolean isWinner()
	{
		//int mostPoints = 0;
		int highestPoints = 0;
		int potentialWinner = 0;
		boolean isWinner = false;
		System.out.println("Test");
		if( numPlayers == 4 && teams.length == 2)
		   victory = 15;

		//int[] teamPoints = new int[teams.length];

		for( int t = 0; t < teams.length; t++ )
		{
		   if( teams[t].score > highestPoints )
		   {
			highestPoints = teams[t].score;
			potentialWinner = t;
		   }
		}

		if( highestPoints >= victory )
		{
			isWinner = true;
		   for( int c = 0; c < teams.length; c++ )
		   {
			if( c != potentialWinner )
			{
				if((teams[potentialWinner].score - teams[c].score) < 2)
					return false;
			}
		   }
		}

		if( isWinner == true )
		   System.out.println(teams[potentialWinner].teamName + " WINS!");

		return isWinner;
	}
	
	*/
	
}