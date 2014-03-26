exports.draw_model = function(d3, el, char) {
	var w = 512
  var h = w;
  var zx = w / 2;
  var zy = h / 2;
  var s = 50;
  var r = w / 2 - s;
  var color = '#FFF';

	var svg = d3.select(el).append('svg')
		.attr('width', w)
		.attr('height', h);


  var elCircle = svg.append('circle')
    .attr('cx', zx)
    .attr('cy', zy)
    .attr('r', r)
    .attr('fill', 'none')
    .attr('stroke', color)
    .attr('stroke-width', s)
    ;

  var textH = r * 2 * 0.8;
  var elText = svg.append('text')
    .attr('x', zx)
    .attr('y', zy)
    .attr('text-anchor', 'middle')
    .attr('font-family', 'sans-serif')
    .attr('font-size', textH)
    .attr('fill', color)
    .attr('stroke', color)
    .attr('stroke-width', s / 2)
    .text(char)
    ;
  if (char != '$') {
    elText.attr('dy', textH / 3 + textH / 20);
  } else {
    elText.attr('dy', textH / 3);
  }
}
