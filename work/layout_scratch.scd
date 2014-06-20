w = Window.new;
w.layout = VLayout(
    Button().states_([["Foo"]]),
    20,
	    TextView().string_("Bar\nBar\nBar\n"),
    nil
);
w.front

mPage.layout_(
	VLayout([
		Button,
		HLayout([
			TextField(), 0.3, Button()
		]),
		0.2,
		Slider()
		], Rect() // optionally give the layout size and placement on the page
	),
)

layout_(|lay|
	var remVSpace, remHSpace;
	var numVElem, numHElem, parentVSpace, parentHSpace;

	lay.bounds.notNil.if(
		{	remVSpace = bounds.height;
			remHSpace = bounds.width;
		},{ remVSpace = remHSpace = 1 }
	);

	// gather V elem
)


layout.elements.do{|elem, i|
	elem.bounds.notNil.if({
		var thisW, thisH;
		thisW = bounds.width*remHSpace;
		thisH = bounds.height*remVSpace;
		elem.new(layout.parent, Rect(
			(1-remHSpace).clip(0,1) + (bounds.x*remHSpace), // x
			1-remVSpace.clip(0,1) + (bounds.y*remVSpace), // y
			thisW, thisH
			)
		)
		remHSpace = (remHSpace - thisW).clip(0,1);
		remVSpace = (remVSpace - thisH).clip(0,1);
		},{
			elem.new(layout.parent, Rect(1-remHSpace, 1-remVSpace, remHSpace, remVSpace))
	});
}

wsLayout { |layout|
	layout.bounds.notNil.if(
		{	remVSpace = bounds.height;
			remHSpace = bounds.width;
			startX = bounds.x;
			startY = bounds.y;
		},{ remVSpace = remHSpace = 1; startX = starty = 0; }
	);

	init {
		layout.isKindOf(wsHLayout).if{ this.buildHLayout (layout, startX, startY, remHSpace, remVSpace) };
		layout.isKindOf(wsVLayout).if{ this.buildVLayout (layout, startX, startY, remHSpace, remVSpace) };
	}

	buildHLayout { |layout, parX, parY, parW, parH|
		var hSpaces, freeSpace, nilSize;
		var nextX, nextY;

		nItems = layout.size;
		hSpaces = layout.collect{|elem|
			if( elem.isKindOf(WsHLayout) or: elem.isKindOf(WsVLayout),
				{ elem.bounds.notNil.if(
					{ elem.bounds.width },
					{ nil }
					);
				},
				{ elem }
			);
		};
		freeSpace = (1 - hSpaces.select({|width| width.notNil}).sum).clip(0,1);
		nilSize = freeSpace / hSpaces.occurrencesOf(nil);

		// place the widgets
		nextX = parX;
		nextY = parY;
		layout.do{|elem, i|
			switch( elem.class,
				Number, 	{nextX = nextX + (elem * parW)}, // advance the x pointer (empty space)
				Nil,		{nextX = nextX + (nilSize * parW)}, // advance the x pointer (empty space)
				WsHLayout, 	{
					this.buildHLayout(elem, nextX, nextY, (hSpaces[i] ?? nilSize) * parW, parH);
				},
				WsVLayout, 	{
					this.buildVLayout(elem, nextX, nextY, (hSpaces[i] ?? nilSize) * parW, parH);
				},
				WsWidget, {
					var myBounds, myWidth;
					elem.bounds.notNil.if(
						{
							myWidth = elem.bounds.width * parW;
							// ignoring original xy for now
							myBounds = Rect(nextX, nextY, myWidth, elem.bounds.height * parH);
						},{
							myWidth =  (hSpaces[i] ?? nilSize) * parW;
							myBounds = Rect(nextX, nextY, myWidth, parH);
					});
					elem.addToPage(wsGUI, boundds: myBounds);
					nextX = nextX + myWidth;
				}
			);
		};
	}
}
