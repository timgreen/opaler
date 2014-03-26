exports.draw_logo = function(d3, el, inlinedTrainIcon, inlinedBadge) {
	var w = 512
  var h = w;
  var r = 50 * 4;
  var zx = w / 2;
  var zy = h / 2;
  var l = r * Math.SQRT1_2;

	var svg = d3.select(el).append('svg')
		.attr('width', w)
		.attr('height', h);

  var colors8 = [
    '#E21A51',
    '#00B3EB',
    '#008BCF',
    '#5FB946',
    '#077F4F',
    '#54B647',
    '#F7941D',
    '#F05423',
  ];

  var p = 8;
  var moveDelta = 0.2
  var rr = 1

  var defs = svg.append('defs');
  defs.append('circle')
    .attr('id', 'cc')
    .attr('cx', zx)
    .attr('cy', zy)
    .attr('r', r * rr)
    ;

  for (var i = 0; i < p; ++i) {
    var dx = Math.cos((i - p / 4) * Math.PI * 2 / p) * r * moveDelta;
    var dy = Math.sin((i - p / 4) * Math.PI * 2 / p) * r * moveDelta;

    defs.append('circle')
      .attr('id', 'c' + i)
      .attr('cx', zx + dx)
      .attr('cy', zy + dy)
      .attr('r', r)
      ;

    var m = defs.append('mask')
      .attr('id', 'm' + i);
    m.append('rect')
      .attr('x', 0)
      .attr('y', 0)
      .attr('width', '100%')
      .attr('height', '100%')
      .attr('fill', 'white')
    m.append('use')
      .attr('xlink:xlink:href', '#c' + i)
      .attr('fill', 'black')
    m.append('use')
      .attr('xlink:xlink:href', '#cc')
      .attr('fill', 'black')
      ;
  }

  for (var i = 0; i < p; ++i) {
    var dx = Math.cos((i - p / 4) * Math.PI * 2 / p) * r * moveDelta;
    var dy = Math.sin((i - p / 4) * Math.PI * 2 / p) * r * moveDelta;

    svg.append('use')
      .attr('xlink:xlink:href', '#c' + i)
      .attr('fill', colors8[i])
      .attr('mask', 'url(#m' + ((i + 1) % p) +')')
      ;
  }

  svg.append('use')
    .attr('xlink:xlink:href', '#cc')
    .attr('fill', 'black')
    .attr('opacity', 0.5)
    ;

  // inline train icon.
  var iy = l * 2;
  var iw = iy * 310 / 475;
  var transforms = [
    'translate(', zx - iw / 2, zy - iy / 2, ')',
    'scale(', iy / 475, ')',
  ];
  var g = svg.append('g');
  g.attr('transform', transforms.join(' '));
  g[0][0].innerHTML = inlinedTrainIcon;

  // alpha beta
  if (inlinedBadge) {
    var bw = w / 2.5;
    var bh = h / 2.5;
    var g = svg.append('g');
    var transforms = [
      'translate(', zx + (w / 2 - bw), zy + (h / 2 - bh), ')',
      'scale(', bw / 140, ')',
    ];
    g.attr('transform', transforms.join(' '));
    g[0][0].innerHTML = inlinedBadge;
  }
}
